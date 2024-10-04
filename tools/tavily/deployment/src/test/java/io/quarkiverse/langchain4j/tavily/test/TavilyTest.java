package io.quarkiverse.langchain4j.tavily.test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class TavilyTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addAsResource(new StringAsset(
                                    """
                                            quarkus.langchain4j.tavily.api-key=test
                                            quarkus.langchain4j.tavily.max-results=9
                                            quarkus.langchain4j.tavily.base-url=%s
                                            quarkus.langchain4j.tavily.include-domains=yeah.com
                                            quarkus.langchain4j.tavily.exclude-domains=nah.com
                                            """.formatted(WiremockAware.wiremockUrlForConfig())),
                                    "application.properties"));

    @Inject
    WebSearchEngine webSearchEngine;

    @BeforeEach
    public void setup() {
        wiremock().register(
                post(urlEqualTo("/search"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                    {
                                                         "query": "Who is Leo Messi?",
                                                         "follow_up_questions": null,
                                                         "answer": null,
                                                         "images": [],
                                                         "results": [
                                                             {
                                                                 "title": "Lionel Messi | Biography, Barcelona, PSG, Ballon d'Or, Inter Miami ...",
                                                                 "url": "https://www.britannica.com/biography/Lionel-Messi",
                                                                 "content": "In early 2009 Messi capped off a spectacular 2008–09 season by helping FC Barcelona capture the club’s first “treble” (winning three major European club titles in one season): the team won the La Liga championship, the Copa del Rey (Spain’s major domestic cup), and the Champions League title. Messi’s play continued to rapidly improve over the years, and by 2008 he was one of the most dominant players in the world, finishing second to Manchester United’s Cristiano Ronaldo in the voting for the 2008 Ballon d’Or. At the 2014 World Cup, Messi put on a dazzling display, scoring four goals and almost single-handedly propelling an offense-deficient Argentina team through the group stage and into the knockout rounds, where Argentina then advanced to the World Cup final for the first time in 24 years. After Argentina was defeated in the Copa final—the team’s third consecutive finals loss in a major tournament—Messi said that he was quitting the national team, but his short-lived “retirement” lasted less than two months before he announced his return to the Argentine team. Messi helped Barcelona capture another treble during the 2014–15 season, leading the team with 43 goals scored over the course of the campaign, which resulted in his fifth world player of the year honour.",
                                                                 "score": 0.98564,
                                                                 "raw_content": null
                                                             },
                                                             {
                                                                 "title": "Lionel Messi and the unmistakeable sense of an ending",
                                                                 "url": "https://www.nytimes.com/athletic/5637953/2024/07/15/lionel-messi-argentina-ending-injury/",
                                                                 "content": "First, he sank to the ground, grimacing. Play continued for a few seconds and then came the communal gasp. Lionel Messi was down. And Lionel Messi is not a player who goes down for nothing ...",
                                                                 "score": 0.98369,
                                                                 "raw_content": null
                                                             },
                                                             {
                                                                 "title": "Lionel Messi: Biography, Soccer Player, Inter Miami CF, Athlete",
                                                                 "url": "https://www.biography.com/athletes/lionel-messi",
                                                                 "content": "The following year, after Messi heavily criticized the referees in the wake of a 2-0 loss to Brazil in the Copa America semifinals, the Argentine captain was slapped with a three-game ban by the South American Football Confederation.\\n So, at the age of 13, when Messi was offered the chance to train at soccer powerhouse FC Barcelona’s youth academy, La Masia, and have his medical bills covered by the team, Messi’s family picked up and moved across the Atlantic to make a new home in Spain. Famous Athletes\\nDennis Rodman\\nBrett Favre\\nTiger Woods\\nJohn McEnroe\\nKurt Warner\\nSandy Koufax\\n10 Things You Might Not Know About Travis Kelce\\nPeyton Manning\\nJames Harden\\nKobe Bryant\\nStephen Curry\\nKyrie Irving\\nA Part of Hearst Digital Media\\n Their marriage, a civil ceremony dubbed by Argentina’s Clarín newspaper as the “wedding of the century,” was held at a luxury hotel in Rosario, with a number of fellow star soccer players and Colombian pop star Shakira on the 260-person guest list.\\n In 2013, the soccer great came back to earth somewhat due to the persistence of hamstring injuries, but he regained his record-breaking form by becoming the all-time leading scorer in La Liga and Champions League play in late 2014.\\n",
                                                                 "score": 0.97953,
                                                                 "raw_content": null
                                                             },
                                                             {
                                                                 "title": "Lionel Messi - Wikipedia",
                                                                 "url": "https://en.wikipedia.org/wiki/Lionel_Messi",
                                                                 "content": "He scored twice in the last group match, a 3–2 victory over Nigeria, his second goal coming from a free kick, as they finished first in their group.[423] Messi assisted a late goal in extra time to ensure a 1–0 win against Switzerland in the round of 16, and played in the 1–0 quarter-final win against Belgium as Argentina progressed to the semi-final of the World Cup for the first time since 1990.[424][425] Following a 0–0 draw in extra time, they eliminated the Netherlands 4–2 in a penalty shootout to reach the final, with Messi scoring his team's first penalty.[426]\\nBilled as Messi versus Germany, the world's best player against the best team, the final was a repeat of the 1990 final featuring Diego Maradona.[427] Within the first half-hour, Messi had started the play that led to a goal, but it was ruled offside. \\"[582] Moreover, several pundits and footballing figures, including Maradona, questioned Messi's leadership with Argentina at times, despite his playing ability.[583][584][585] Vickery states the perception of Messi among Argentines changed in 2019, with Messi making a conscious effort to become \\"more one of the group, more Argentine\\", with Vickery adding that following the World Cup victory in 2022 Messi would now be held in the same esteem by his compatriots as Maradona.[581]\\nComparisons with Cristiano Ronaldo\\nAmong his contemporary peers, Messi is most often compared and contrasted with Portuguese forward Cristiano Ronaldo, as part of an ongoing rivalry that has been compared to past sports rivalries like the Muhammad Ali–Joe Frazier rivalry in boxing, the Roger Federer–Rafael Nadal rivalry in tennis, and the Prost–Senna rivalry from Formula One motor racing.[586][587]\\nAlthough Messi has at times denied any rivalry,[588][589] they are widely believed to push one another in their aim to be the best player in the world.[160] Since 2008, Messi has won eight Ballons d'Or to Ronaldo's five,[590] seven FIFA World's Best Player awards to Ronaldo's five, and six European Golden Shoes to Ronaldo's four.[591] Pundits and fans regularly argue the individual merits of both players.[160][592] On 11 July, Messi provided his 20th assist of the league season for Arturo Vidal in a 1–0 away win over Real Valladolid, equalling Xavi's record of 20 assists in a single La Liga season from 2008 to 2009;[281][282] with 22 goals, he also became only the second player ever, after Thierry Henry in the 2002–03 FA Premier League season with Arsenal (24 goals and 20 assists), to record at least 20 goals and 20 assists in a single league season in one of Europe's top-five leagues.[282][283] Following his brace in a 5–0 away win against Alavés in the final match of the season on 20 May, Messi finished the season as both the top scorer and top assist provider in La Liga, with 25 goals and 21 assists respectively, which saw him win his record seventh Pichichi trophy, overtaking Zarra; however, Barcelona missed out on the league title to Real Madrid.[284] On 7 March, two weeks after scoring four goals in a league fixture against Valencia, he scored five times in a Champions League last 16-round match against Bayer Leverkusen, an unprecedented achievement in the history of the competition.[126][127] In addition to being the joint top assist provider with five assists, this feat made him top scorer with 14 goals, tying José Altafini's record from the 1962–63 season, as well as becoming only the second player after Gerd Müller to be top scorer in four campaigns.[128][129] Two weeks later, on 20 March, Messi became the top goalscorer in Barcelona's history at 24 years old, overtaking the 57-year record of César Rodríguez's 232 goals with a hat-trick against Granada.[130]\\nDespite Messi's individual form, Barcelona's four-year cycle of success under Guardiola – one of the greatest eras in the club's history – drew to an end.[131] He still managed to break two longstanding records in a span of seven days: a hat-trick on 16 March against Osasuna saw him overtake Paulino Alcántara's 369 goals to become Barcelona's top goalscorer in all competitions including friendlies, while another hat-trick against Real Madrid on 23 March made him the all-time top scorer in El Clásico, ahead of the 18 goals scored by former Real Madrid player Alfredo Di Stéfano.[160][162] Messi finished the campaign with his worst output in five seasons, though he still managed to score 41 goals in all competitions.[161][163] For the first time in five years, Barcelona ended the season without a major trophy; they were defeated in the Copa del Rey final by Real Madrid and lost the league in the last game to Atlético Madrid, causing Messi to be booed by sections of fans at the Camp Nou.[164]",
                                                                 "score": 0.97579,
                                                                 "raw_content": null
                                                             },
                                                             {
                                                                 "title": "The life and times of Lionel Messi",
                                                                 "url": "https://www.nytimes.com/athletic/4783674/2023/08/18/lionel-messi-profile-soccer/",
                                                                 "content": "For Messi, it is major trophy number 44.. Despite turning 36 in June, he is as influential as ever. Here is the complete story of Lionel Andres Messi, widely regarded as one of the greatest ...",
                                                                 "score": 0.96961,
                                                                 "raw_content": null
                                                             }
                                                         ],
                                                         "response_time": 0.88
                                                     }
                                                """)));
    }

    @Test
    public void testSearch() {
        WebSearchRequest searchRequest = WebSearchRequest.builder()
                .searchTerms("testing-query")
                .build();
        WebSearchResults result = webSearchEngine.search(searchRequest);
        LoggedRequest actualRequest = singleLoggedRequest();
        System.out.println(actualRequest);

        // verify the request
        assertEquals(actualRequest.getHeader("Accept"), "application/json");
        assertEquals(actualRequest.getHeader("Content-Type"), "application/json");
        assertEquals(actualRequest.getUrl(), "/search");
        assertEquals(actualRequest.getMethod().toString(), "POST");
        String body = actualRequest.getBodyAsString();
        assertThat(body).contains("\"api_key\":\"test\"");
        assertThat(body).contains("\"max_results\":9");
        assertThat(body).contains("\"query\":\"testing-query\"");
        assertThat(body).contains("\"include_domains\":[\"yeah.com\"]");
        assertThat(body).contains("\"exclude_domains\":[\"nah.com\"]");

        // verify the parsed response
        assertThat(result.searchInformation().totalResults()).isEqualTo(5);
        WebSearchOrganicResult firstResult = result.results().get(0);
        assertThat(firstResult.title().equals("Lionel Messi | Biography, Barcelona, PSG, Ballon d'Or, Inter Miami ..."));
        assertThat(firstResult.url().equals("https://www.britannica.com/biography/Lionel-Messi"));
        assertThat(firstResult.snippet()
                .equals("""
                        In early 2009 Messi capped off a spectacular 2008–09 season by helping \
                        FC Barcelona capture the club’s first “treble” (winning three major European club titles in one season): \
                        the team won the La Liga championship, the Copa del Rey (Spain’s major domestic cup), and the Champions League title. \
                        Messi’s play continued to rapidly improve over the years, and by 2008 he was one of the most dominant players in the world, \
                        finishing second to Manchester United’s Cristiano Ronaldo in the voting for the 2008 Ballon d’Or. At the 2014 World Cup, Messi \
                        put on a dazzling display, scoring four goals and almost single-handedly propelling an offense-deficient Argentina team through \
                        the group stage and into the knockout rounds, where Argentina then advanced to the World Cup final for the first time in 24 years. \
                        After Argentina was defeated in the Copa final—the team’s third consecutive finals loss in a major tournament—Messi said that \
                        he was quitting the national team, but his short-lived “retirement” lasted less than two months before he announced his return \
                        to the Argentine team. Messi helped Barcelona capture another treble during the 2014–15 season, leading the team with 43 goals \
                        scored over the course of the campaign, which resulted in his fifth world player of the year honour."""));
        assertThat(firstResult.metadata().get("score").equals("0.98564"));
    }

}
