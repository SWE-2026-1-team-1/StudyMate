package com.studymate.study.fixture;

import com.studymate.study.domain.Tag;
import com.studymate.study.repository.TagRepository;

public class TagFixture {

    private TagFixture() {}

    public static Tag persist(String name, TagRepository repo) {
        return repo.findByName(name).orElseGet(() -> repo.save(Tag.create(name)));
    }
}
