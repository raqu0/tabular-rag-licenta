package com.licenta.tabular_rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatLanguageModel chatModel;
    private final CsvService csvService;

    private EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    // NOU: Memoria conversației
    private ChatMemory chatMemory;

    public ChatController(CsvService csvService) {
        this.csvService = csvService;

        this.chatModel = OpenAiChatModel.builder()
                .apiKey("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")
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("llama-3.3-70b-versatile")
                .temperature(0.0)
                .build();

        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // NOU: Inițializăm memoria să țină minte ultimele 10 mesaje (5 întrebări + 5 răspunsuri)
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);
    }

    @PostMapping("/upload")
    public String uploadCsv(@RequestParam("file") MultipartFile file) {
        try {
            List<String> date = csvService.proceseazaCsv(file);

            // NOU: Resetăm baza de date vectorială ȘI memoria când se încarcă un fișier nou
            this.embeddingStore = new InMemoryEmbeddingStore<>();
            this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);

            System.out.println("Incepem indexarea vectoriala...");
            for (String rand : date) {
                TextSegment segment = TextSegment.from(rand);
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            }
            return "Fișier procesat cu succes! Am indexat vectorial " + date.size() + " rânduri.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare la procesarea fișierului: " + e.getMessage();
        }
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String intrebare) {
        try {
            // NOU: PASUL 1 - Query Rewriting (Transformarea intrebarii)
            // AI-ul se uita in istoric si inlocuieste "el/acesta" cu numele real inainte sa cautam in baza de date
            String promptRescriere = "Analizează acest istoric al conversației:\n" + chatMemory.messages().toString() +
                    "\n\nNoua întrebare a utilizatorului este: \"" + intrebare + "\"\n" +
                    "Dacă noua întrebare folosește pronume (acesta, el, ea) sau depinde de contextul anterior, rescrie-o pentru a include numele exact al persoanei sau subiectului din istoric. " +
                    "Dacă este deja o întrebare clară de sine stătătoare, las-o cum e. Returnează STRICT doar întrebarea rescrisă, fără niciun alt text introductiv.";

            String intrebareClara = chatModel.generate(promptRescriere);
            System.out.println("----- DEBUG RAG -----");
            System.out.println("User a scris: " + intrebare);
            System.out.println("AI a rescris pentru baza de date: " + intrebareClara);

            // PASUL 2 - Vector Search cu intrebarea rescrisa
            var intrebareEmbedding = embeddingModel.embed(intrebareClara).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(intrebareEmbedding, 5);

            StringBuilder contextRelevant = new StringBuilder();
            for (EmbeddingMatch<TextSegment> match : matches) {
                contextRelevant.append(match.embedded().text()).append("\n");
            }

            // PASUL 3 - Construim pachetul final și adăugăm în memorie
            String continutMesaj = "Folosind DOAR următoarele informații extrase din baza de date:\n" +
                    contextRelevant.toString() + "\n\n" +
                    "Răspunde la întrebarea: " + intrebareClara;

            chatMemory.add(UserMessage.from(continutMesaj));
            AiMessage raspunsAI = chatModel.generate(chatMemory.messages()).content();
            chatMemory.add(raspunsAI);

            return raspunsAI.text();

        } catch (Exception e) {
            e.printStackTrace();
            return "Eroare la generarea raspunsului: " + e.getMessage();
        }
    }
}