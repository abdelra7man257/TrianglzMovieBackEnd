package com.example.trianglzmoviebackend.config

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

/** Creates the FCM client only when FIREBASE_CREDENTIALS_PATH is set; otherwise pushes are log-only. */
@Configuration
class FirebaseConfig {

    @Bean
    @ConditionalOnExpression("!'\${app.firebase.credentials-path:}'.isBlank()")
    fun firebaseMessaging(properties: AppProperties): FirebaseMessaging {
        val credentials = FileInputStream(properties.firebase.credentialsPath).use(GoogleCredentials::fromStream)
        // The plain JDK transport avoids the Apache HttpClient that Spring's dependency management upgrades,
        // which makes the Firebase SDK decompress FCM responses twice ("Not in GZIP format").
        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setHttpTransport(NetHttpTransport())
            .build()
        val app = FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
        return FirebaseMessaging.getInstance(app)
    }
}
