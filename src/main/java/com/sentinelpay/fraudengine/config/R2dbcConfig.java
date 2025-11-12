package com.sentinelpay.fraudengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        // No custom converters needed since we're using String directly
        // PostgreSQL will automatically handle String -> JSONB conversion
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE);
    }
}