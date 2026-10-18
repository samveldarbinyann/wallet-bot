package com.example.walletbot.platform.web.security;

import com.example.walletbot.user.domain.UserEntity;
import com.example.walletbot.user.infrastructure.UserCacheService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    public static final String CURRENT_USER_ATTR = "currentUser";

    private final UserCacheService userCacheService;

    public CurrentUserResolver(UserCacheService userCacheService) {
        this.userCacheService = userCacheService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(UserEntity.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Object stashed = webRequest.getAttribute(CURRENT_USER_ATTR, RequestAttributes.SCOPE_REQUEST);
        if (stashed instanceof UserEntity user) {
            return user;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser authUser) {
            return userCacheService.findById(authUser.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated user found");
    }
}
