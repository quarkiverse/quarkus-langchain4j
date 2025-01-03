import {LitElement, html, css} from 'lit';

export class DemoTitle extends LitElement {

    static styles = css`
      h1 {
        font-family: "Red Hat Mono", monospace;
        font-size: 60px;
        font-style: normal;
        font-variant: normal;
        font-weight: 700;
        line-height: 26.4px;
        color: var(--main-highlight-text-color);
      }

      .title {
        text-align: center;
        padding: 1em;
        background: var(--main-bg-color);
      }
      
      .explanation {
        margin-left: auto;
        margin-right: auto;
        width: 50%;
        text-align: justify;
        font-size: 20px;
      }
      
      .explanation img {
        max-width: 60%;
        display: block;
        float:left;
        margin-right: 2em;
        margin-top: 1em;
      }
    `

    render() {
        return html`
            <div class="title">
                <h1>Movie Muse</h1>
            </div>
            <div class="explanation">
                This demo shows how to build a chatbot powered by GPT 4o-mini and advanced retrieval augmented generation.
                The application ingested a CSV files listing the top 100 movies from IMDB.
                You can now ask questions about the movies and the application will answer.
                For example <em>What is the rating of the movie Inception?</em> or <em>What comedy movies are in the database?</em>
            </div>
            
            <div class="explanation">
                <img src="images/chatbot-architecture.png"/>
            </div>
            
            <div class="explanation">
                <ol>
                    <li>The user sends a question.</li>
                    <li>The question and previous chat history go through a query compressing transformer to extract a concise question with all the relevant context.</li>
                    <li>LLM is used to perform the compression.</li>
                    <li>Now we need to generate a SQL query to fetch the relevant data.</li>
                    <li>LLM is asked to generate the SQL query.</li>
                    <li>The content retriever executes the generated SQL query.</li>
                    <li>The fetched data is added back to the original question as a set of JSON objects.</li>
                    <li>The question + fetched data are submitted to the LLM.</li>
                    <li>The LLM's response is sent back to the user.</li>
                </ol>
            </div>
        `
    }
}

customElements.define('demo-title', DemoTitle);