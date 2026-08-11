package com.recruitment.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Serve file da upload (logo cong ty...) qua /uploads/**. Dung Path.toUri() thay vi tu noi chuoi
// "file:" + path de luon co dung dau "/" cuoi - thieu no la 404 im lang, loi rat hay gap.
@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    private final LocalStorageService localStorageService;

    public StorageWebConfig(LocalStorageService localStorageService) {
        this.localStorageService = localStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(localStorageService.getBasePath().toUri().toString());
    }
}
