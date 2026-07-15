package com.licenta.tabular_rag;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class SecuritateTests {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    // Test 1: parola criptata NU este egala cu parola in clar
    @Test
    void parolaCriptataNuEsteInClar() {
        String parolaClar = "parola123";
        String hash = encoder.encode(parolaClar);

        assertNotEquals(parolaClar, hash);
        assertTrue(hash.startsWith("$2a$"));
    }

    // Test 2: parola corecta trece verificarea, una gresita nu
    @Test
    void verificareParolaCorectaSiGresita() {
        String hash = encoder.encode("parola123");

        assertTrue(encoder.matches("parola123", hash));   // parola buna
        assertFalse(encoder.matches("altaparola", hash)); // parola gresita
    }

    // Test 3: aceeasi parola produce hash-uri diferite (datorita salt-ului)
    @Test
    void aceeasiParolaProduceHashuriDiferite() {
        String hash1 = encoder.encode("parola123");
        String hash2 = encoder.encode("parola123");

        assertNotEquals(hash1, hash2);                     // salt diferit
        assertTrue(encoder.matches("parola123", hash1));   // ambele valide
        assertTrue(encoder.matches("parola123", hash2));
    }
}