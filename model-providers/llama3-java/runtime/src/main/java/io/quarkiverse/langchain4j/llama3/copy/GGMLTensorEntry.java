package io.quarkiverse.langchain4j.llama3.copy;

import java.lang.foreign.MemorySegment;

public record GGMLTensorEntry(MemorySegment mappedFile, String name, GGMLType ggmlType, int[] shape,
        MemorySegment memorySegment) {
}
