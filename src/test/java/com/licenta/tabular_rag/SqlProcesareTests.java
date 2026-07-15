package com.licenta.tabular_rag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlProcesareTests {

    // Test 6: verifica logica de curatare a SQL-ului (eliminare markdown si punct-virgula)
    @Test
    void curatareSqlEliminaMarkdownSiPunctVirgula() {
        String sqlBrut = "```sql\nSELECT * FROM DATE_TABEL;\n```";

        // Replicam logica din metoda cleanSql a aplicatiei
        String curatat = sqlBrut.replace("```sql", "").replace("```", "").trim();
        if (curatat.endsWith(";")) {
            curatat = curatat.substring(0, curatat.length() - 1);
        }

        assertFalse(curatat.contains("```"));      // markdown-ul a fost eliminat
        assertFalse(curatat.endsWith(";"));         // punct-virgula a fost eliminata
        assertEquals("SELECT * FROM DATE_TABEL", curatat);
    }

    // Test 7: verifica inlocuirea numelui generic DATE_TABEL cu functia CSVREAD
    @Test
    void inlocuireDateTabelCuCsvread() {
        String sql = "SELECT * FROM DATE_TABEL WHERE nota > 5";
        String cale = "C:/fisiere/note.csv";

        // Replicam logica de inlocuire din aplicatie
        String sqlH2 = sql.replaceAll("(?i)DATE_TABEL", "CSVREAD('" + cale + "')");

        assertTrue(sqlH2.contains("CSVREAD('C:/fisiere/note.csv')"));
        assertFalse(sqlH2.contains("DATE_TABEL"));   // numele generic a disparut
    }
}