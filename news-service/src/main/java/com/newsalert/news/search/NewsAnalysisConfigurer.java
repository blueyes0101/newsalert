package com.newsalert.news.search;

import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurationContext;
import org.hibernate.search.backend.elasticsearch.analysis.ElasticsearchAnalysisConfigurer;

/**
 * Custom Elasticsearch analysis configuration for the news-service.
 *
 * <p>I registered this configurer via {@code application.properties}:
 * {@code quarkus.hibernate-search-orm.elasticsearch.analysis.configurer=
 * class:com.newsalert.news.search.NewsAnalysisConfigurer}</p>
 *
 * <p>The {@code news_analyzer} pipeline has three stages:</p>
 * <ol>
 *   <li><b>standard tokenizer</b> — splits input on whitespace and punctuation,
 *       e.g. "Hibernate-Search" → ["Hibernate", "Search"]</li>
 *   <li><b>lowercase</b> — folds all tokens to lower case so "Quarkus" matches "quarkus"</li>
 *   <li><b>asciifolding</b> — strips diacritics, e.g. "Zürich" → "zurich",
 *       "café" → "cafe"; useful for multilingual news content</li>
 *   <li><b>news_english_stemmer</b> — reduces words to their root form:
 *       "running" → "run", "searches" → "search", "releases" → "releas".
 *       This improves recall without hurting precision noticeably for tech content.</li>
 * </ol>
 *
 * <p>I chose the English stemmer because most of the crawled news content is in English.
 * Switching to a German stemmer (or stacking both via a language-detect char filter) would
 * be the next step if the keyword set grows to include German-language sources.</p>
 */
public class NewsAnalysisConfigurer implements ElasticsearchAnalysisConfigurer {

    @Override
    public void configure(ElasticsearchAnalysisConfigurationContext context) {

        // Define a reusable stemmer token filter for the news domain.
        // "english" maps to the Porter2 (Snowball) stemmer in Elasticsearch.
        context.tokenFilter("news_english_stemmer")
                .type("stemmer")
                .param("language", "english");

        // Assemble the full analyzer pipeline.
        // Lowercase + asciifolding handles case and accents ("Zürich" matches "zurich").
        // The English stemmer reduces words to roots ("running" matches "run").
        context.analyzer("news_analyzer").custom()
                .tokenizer("standard")
                .tokenFilters("lowercase", "asciifolding", "news_english_stemmer");
    }
}
