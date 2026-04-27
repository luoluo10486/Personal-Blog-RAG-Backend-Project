package com.personalblog.ragbackend.knowledge.service.vector;

import com.personalblog.ragbackend.knowledge.config.KnowledgeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeVectorSpaceResolverTest {

    @Test
    void shouldResolveDefaultBaseToConfiguredCollection() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setDefaultBaseCode("default");
        properties.getDefaults().setCollectionName("knowledge_default_store");
        properties.getVector().setType("milvus");
        properties.getVector().getMilvus().setDatabaseName("rag_prod");

        KnowledgeVectorSpaceResolver resolver = new KnowledgeVectorSpaceResolver(properties);

        KnowledgeVectorSpace vectorSpace = resolver.resolve("default");

        assertThat(vectorSpace.collectionName()).isEqualTo("knowledge_default_store");
        assertThat(vectorSpace.spaceId().logicalName()).isEqualTo("default");
        assertThat(vectorSpace.spaceId().namespace()).isEqualTo("rag_prod");
    }

    @Test
    void shouldNormalizeCustomBaseCodeAndKeepLogicalCollectionNaming() {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setDefaultBaseCode("default");
        properties.getVector().setType("pgvector");
        properties.getVector().getMilvus().setCollectionPrefix("kb_");
        properties.getVector().getPg().setSchema("knowledge");

        KnowledgeVectorSpaceResolver resolver = new KnowledgeVectorSpaceResolver(properties);

        KnowledgeVectorSpace vectorSpace = resolver.resolve(" HR Policy ");

        assertThat(vectorSpace.collectionName()).isEqualTo("kb_hr_policy");
        assertThat(vectorSpace.spaceId().logicalName()).isEqualTo("hr_policy");
        assertThat(vectorSpace.spaceId().namespace()).isEqualTo("knowledge");
    }
}
