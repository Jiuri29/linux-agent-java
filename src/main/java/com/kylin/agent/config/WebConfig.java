package com.kylin.agent.config;

import com.kylin.agent.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AppProperties properties;

    public WebConfig(AuthInterceptor authInterceptor, AppProperties properties) {
        this.authInterceptor = authInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = new File(properties.frontendDir()).getAbsolutePath().replace('\\', '/');
        registry.addResourceHandler("/**")
                .addResourceLocations("file:" + dir + "/", "classpath:/static/");
    }
}
