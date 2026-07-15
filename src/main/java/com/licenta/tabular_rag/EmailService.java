package com.licenta.tabular_rag;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordRecoveryEmail(String toEmail, String newPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@nexus-rag.com");
        message.setTo(toEmail);
        message.setSubject("Recuperare Parolă - Nexus RAG");
        message.setText("Salut,\n\n" +
                "Ai solicitat resetarea parolei pentru contul tău.\n" +
                "Noua ta parolă generată automat este: " + newPassword + "\n\n" +
                "Te rugăm să te loghezi și să îți schimbi parola cât mai curând.\n\n" +
                "Echipa Nexus RAG");

        mailSender.send(message);
    }
}