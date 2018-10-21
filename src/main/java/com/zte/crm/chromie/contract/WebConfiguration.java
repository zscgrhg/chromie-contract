package com.zte.crm.chromie.contract;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.WebMvcRegistrations;
import org.springframework.boot.autoconfigure.web.WebMvcRegistrationsAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
@ConditionalOnProperty(name = "handler.mapping.controller.only",
        havingValue = "true",
        matchIfMissing = true)
public class WebConfiguration {

    @Bean
    public WebMvcRegistrations mappingOnlyOnController() {
        return new WebMvcRegistrationsAdapter() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new RequestMappingHandlerMapping() {
                    protected boolean isHandler(Class<?> beanType) {
                        return (AnnotatedElementUtils.hasAnnotation(beanType, Controller.class));
                    }
                };
            }
        };
    }

}
