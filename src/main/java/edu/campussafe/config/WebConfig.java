package edu.campussafe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebConfig implements WebMvcConfigurer {
  @Value("${app.client-url}") String client;
  @Override public void addCorsMappings(CorsRegistry registry){
    registry.addMapping("/api/**").allowedOriginPatterns(client,"https://*.vercel.app","http://localhost:*","http://127.0.0.1:*").allowedMethods("GET","POST","PATCH","OPTIONS").allowedHeaders("*");
    registry.addMapping("/uploads/**").allowedOriginPatterns(client,"https://*.vercel.app","http://localhost:*","http://127.0.0.1:*").allowedMethods("GET","OPTIONS");
  }
  @Override public void addResourceHandlers(ResourceHandlerRegistry registry){registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");}
}


