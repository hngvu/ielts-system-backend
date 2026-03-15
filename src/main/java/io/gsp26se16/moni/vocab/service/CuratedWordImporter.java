package io.gsp26se16.moni.vocab.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.vocab.entity.CuratedWord;
import io.gsp26se16.moni.vocab.repository.CuratedWordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CuratedWordImporter implements ApplicationRunner {

    private static final String CSV_URL =
            "https://raw.githubusercontent.com/Maximax67/Words-CEFR-Dataset/main/datasets/word_list_cefr.csv";

    private static final Map<String, String> BAND_MAP = Map.of(
            "A2", "3-4",
            "B1", "4-5",
            "B2", "5.5-6.5",
            "C1", "7-8",
            "C2", "8.5-9");

    private static final Map<String, List<String>> TOPIC_KEYWORDS = Map.ofEntries(
            Map.entry(
                    "environment",
                    List.of(
                            "pollution",
                            "climate",
                            "ecosystem",
                            "forest",
                            "ocean",
                            "carbon",
                            "biodiversity",
                            "renewable",
                            "habitat",
                            "wildlife",
                            "greenhouse",
                            "emission",
                            "recycle",
                            "deforestation",
                            "drought",
                            "flood",
                            "atmosphere",
                            "erosion",
                            "conservation",
                            "sustainable",
                            "ecology",
                            "contamination",
                            "pesticide",
                            "ozone",
                            "landfill")),
            Map.entry(
                    "technology",
                    List.of(
                            "computer",
                            "digital",
                            "software",
                            "internet",
                            "algorithm",
                            "artificial",
                            "robot",
                            "automation",
                            "electronic",
                            "network",
                            "cybersecurity",
                            "database",
                            "wireless",
                            "semiconductor",
                            "innovation",
                            "prototype",
                            "satellite",
                            "bandwidth",
                            "encryption",
                            "interface",
                            "programming",
                            "smartphone",
                            "processor",
                            "virtual",
                            "cloud")),
            Map.entry(
                    "health",
                    List.of(
                            "disease",
                            "medical",
                            "therapy",
                            "hospital",
                            "nutrition",
                            "vaccine",
                            "diagnosis",
                            "surgery",
                            "mental",
                            "pharmaceutical",
                            "chronic",
                            "immune",
                            "symptom",
                            "epidemic",
                            "pandemic",
                            "antibiotic",
                            "prescription",
                            "obesity",
                            "diabetes",
                            "cancer",
                            "cardiovascular",
                            "neurological",
                            "rehabilitation",
                            "psychology",
                            "stress")),
            Map.entry(
                    "education",
                    List.of(
                            "school",
                            "university",
                            "curriculum",
                            "student",
                            "academic",
                            "literacy",
                            "pedagogy",
                            "scholarship",
                            "examination",
                            "tuition",
                            "graduate",
                            "undergraduate",
                            "classroom",
                            "lecture",
                            "diploma",
                            "assessment",
                            "comprehension",
                            "knowledge",
                            "learning",
                            "teaching",
                            "cognitive",
                            "institution",
                            "qualification",
                            "vocational",
                            "training")),
            Map.entry(
                    "business",
                    List.of(
                            "economy",
                            "market",
                            "profit",
                            "investment",
                            "corporate",
                            "entrepreneur",
                            "revenue",
                            "fiscal",
                            "inflation",
                            "recession",
                            "merchandise",
                            "commerce",
                            "negotiate",
                            "bankruptcy",
                            "dividend",
                            "commodity",
                            "supplier",
                            "logistics",
                            "franchise",
                            "retail",
                            "export",
                            "import",
                            "tariff",
                            "tax",
                            "budget")),
            Map.entry(
                    "science",
                    List.of(
                            "experiment",
                            "hypothesis",
                            "laboratory",
                            "research",
                            "chemical",
                            "molecule",
                            "organism",
                            "evolution",
                            "genetics",
                            "quantum",
                            "radioactive",
                            "catalyst",
                            "compound",
                            "periodic",
                            "neutron",
                            "photosynthesis",
                            "gravity",
                            "electromagnetic",
                            "thermal",
                            "biology",
                            "physics",
                            "chemistry",
                            "astronomy",
                            "microscope",
                            "theory")),
            Map.entry(
                    "travel",
                    List.of(
                            "tourist",
                            "destination",
                            "journey",
                            "hotel",
                            "passport",
                            "itinerary",
                            "accommodation",
                            "visa",
                            "departure",
                            "arrival",
                            "luggage",
                            "customs",
                            "immigration",
                            "expedition",
                            "adventure",
                            "sightseeing",
                            "landmark",
                            "cruise",
                            "transit",
                            "reservation")),
            Map.entry(
                    "food",
                    List.of(
                            "cuisine",
                            "ingredient",
                            "recipe",
                            "restaurant",
                            "flavor",
                            "nutritious",
                            "culinary",
                            "harvest",
                            "beverage",
                            "organic",
                            "fermentation",
                            "spice",
                            "dietary",
                            "vegetarian",
                            "vegan",
                            "protein",
                            "carbohydrate",
                            "calorie",
                            "appetite",
                            "gastronomy")),
            Map.entry(
                    "society",
                    List.of(
                            "community",
                            "culture",
                            "tradition",
                            "population",
                            "urban",
                            "immigration",
                            "democracy",
                            "inequality",
                            "poverty",
                            "discrimination",
                            "diversity",
                            "ethnicity",
                            "religion",
                            "politics",
                            "governance",
                            "parliament",
                            "constitution",
                            "legislation",
                            "census",
                            "sociology")),
            Map.entry(
                    "arts",
                    List.of(
                            "music",
                            "painting",
                            "theater",
                            "literature",
                            "creative",
                            "sculpture",
                            "poetry",
                            "cinema",
                            "architecture",
                            "photography",
                            "choreography",
                            "exhibition",
                            "gallery",
                            "manuscript",
                            "aesthetic",
                            "composition",
                            "performance",
                            "narrative",
                            "artistic",
                            "cultural")));

    private final CuratedWordRepository curatedWordRepository;

    @Override
    public void run(ApplicationArguments args) {
        long existing = curatedWordRepository.count();
        if (existing > 0) {
            log.info("CEFR data already imported ({} words). Skipping.", existing);
            return;
        }

        log.info("No curated words found. Importing CEFR dataset...");
        try {
            List<CuratedWord> words = downloadAndParse();
            curatedWordRepository.saveAll(words);
            log.info("CEFR import complete: {} words imported.", words.size());
        } catch (Exception e) {
            log.error("CEFR import failed: {}", e.getMessage(), e);
        }
    }

    private List<CuratedWord> downloadAndParse() throws Exception {
        List<CuratedWord> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try (var in = URI.create(CSV_URL).toURL().openStream();
                var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header
            if (header == null) return result;

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(";", -1);
                if (parts.length < 3) continue;

                String word = parts[0].trim().toLowerCase();
                String pos = parts[1].trim();
                String cefr = parts[2].trim().toUpperCase();

                if (word.isEmpty() || cefr.isEmpty()) continue;
                if (seen.contains(word)) continue;
                seen.add(word);

                String band = BAND_MAP.get(cefr);
                if (band == null) continue; // skip A1 or unknown

                CuratedWord cw = CuratedWord.builder()
                        .word(word)
                        .pos(pos.isEmpty() ? null : pos)
                        .cefrLevel(cefr)
                        .band(band)
                        .topic(detectTopic(word))
                        .build();
                result.add(cw);
            }
        }
        return result;
    }

    private String detectTopic(String word) {
        for (var entry : TOPIC_KEYWORDS.entrySet()) {
            if (entry.getValue().contains(word)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
