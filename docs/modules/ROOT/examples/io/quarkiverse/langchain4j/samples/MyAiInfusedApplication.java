package io.quarkiverse.langchain4j.samples;

public class MyAiInfusedApplication {

    //    @RegisterAiService // <1>
    //    @SystemMessage("You are a professional poet") // <2>
    //    interface MyAiService {
    //        @UserMessage("""
    //                    Write a poem about {topic}.
    //                    The poem should be {lines} lines long. <3>
    //                """)
    //        String writeAPoem(String topic, int lines); // <4>
    //    }
    //
    //    @Inject
    //    MyAiService myAiService; // <5>
    //
    //    void start(@Observes StartupEvent ev) {
    //        System.out.println(myAiService.writeAPoem("quarkus", 4));
    //    }
}
