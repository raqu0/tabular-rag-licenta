package com.licenta.tabular_rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserEntityTests {

    // Test 4: constructorul si getterii functioneaza corect
    @Test
    void creareUtilizatorCuDateCorecte() {
        User user = new User("razvan", "razvan@licenta.ro", "hash123", "ADMIN");

        assertEquals("razvan", user.getUsername());
        assertEquals("razvan@licenta.ro"
                , user.getEmail());
        assertEquals("ADMIN", user.getRole());
    }

    // Test 5: rolul poate fi schimbat prin setter
    @Test
    void schimbareRolUtilizator() {
        User user = new User("marian", "marian@test.ro", "hash456", "USER");
        user.setRole("ADMIN");

        assertEquals("ADMIN", user.getRole());
    }
}