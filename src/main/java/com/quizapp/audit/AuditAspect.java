package com.quizapp.audit;

import com.quizapp.services.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.stream.Collectors;

@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(com.quizapp.audit.Auditable) || @within(com.quizapp.audit.Auditable)")
    public Object aroundAuditable(ProceedingJoinPoint pjp) throws Throwable {
        String username = null;
        try {
            username = SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception ignored) {}

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Auditable ann = method.getAnnotation(Auditable.class);
        String action = (ann != null && !ann.action().isEmpty()) ? ann.action() : method.getName();

        // capture brief args snapshot (avoid huge payloads)
        String argsSummary = "";
        try {
            Object[] args = pjp.getArgs();
            if (args != null && args.length > 0) {
                argsSummary = java.util.Arrays.stream(args)
                        .map(a -> a == null ? "null" : a.getClass().getSimpleName())
                        .collect(Collectors.joining(",", "args=[", "]"));
            }
        } catch (Exception ignored) {}

        // execute method
        Object result = pjp.proceed();

        // create audit record and fire async save
        Audit a = new Audit();
        a.setUsername(username);
        a.setAction(action);
        a.setResourceType(pjp.getTarget() != null ? pjp.getTarget().getClass().getSimpleName() : null);

        // attempt to extract id from result if possible: try getId(), id(), unwrap Optional, or numeric result
        try {
            if (result != null) {
                Object candidate = result;
                if (candidate instanceof Optional opt) {
                    if (opt.isPresent()) candidate = opt.get(); else candidate = null;
                }
                if (candidate != null) {
                    Long idVal = null;
                    try {
                        Method getId = candidate.getClass().getMethod("getId");
                        Object idObj = getId.invoke(candidate);
                        if (idObj instanceof Number) idVal = ((Number) idObj).longValue();
                    } catch (NoSuchMethodException ignored) {
                    }
                    if (idVal == null) {
                        try {
                            Method idMethod = candidate.getClass().getMethod("id");
                            Object idObj = idMethod.invoke(candidate);
                            if (idObj instanceof Number) idVal = ((Number) idObj).longValue();
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (idVal == null && candidate instanceof Number) {
                        idVal = ((Number) candidate).longValue();
                    }
                    if (idVal != null) a.setResourceId(idVal);
                }
            }
        } catch (Exception ignored) {}

        a.setDetails("method=" + method.getName() + ";" + argsSummary);
        auditService.record(a);

        return result;
    }
}
