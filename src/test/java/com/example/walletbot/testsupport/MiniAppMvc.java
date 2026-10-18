package com.example.walletbot.testsupport;

import com.example.walletbot.notification.application.MessageService;
import com.example.walletbot.platform.web.advice.GlobalExceptionHandler;
import com.example.walletbot.user.domain.UserEntity;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class MiniAppMvc {

    private MiniAppMvc() {
    }

    public static MockMvc forController(Object controller, UserEntity currentUser) {
        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(UserEntity.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return currentUser;
            }
        };

        MessageService messageService = mock(MessageService.class);
        when(messageService.get(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(messageService.get(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));

        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(messageService))
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }
}
