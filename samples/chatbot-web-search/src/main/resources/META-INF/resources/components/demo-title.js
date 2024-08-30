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
                <h1>Web search example</h1>
            </div>
            <div class="explanation">
                This demo shows how to build a chatbot that can use the Tavily search
                engine to look up data on the internet that may be relevant for answering
                a user's question. Try opening the chatbot (the red robot button in the 
                bottom right) and ask a question like "Give me yesterday's news headlines".
                As a follow-up question, we suggest for example, to ask for the 
                source URL of one of the returned headline articles.
                
                Observe the Quarkus log to see the interaction. 
                The interaction goes like this:
            </div>
            
            <div class="explanation">
                <ol>
                    <li>The user send a question to the application.</li>
                    <li>If necessary, the model executes the <i>getTodaysDate</i> tool to obtain the current date
                        (this tool resides in the <i>AdditionalTools</i> class in this project).</li>
                    <li>The model transforms the user's question (and 
                        optionally the obtained date) into a query for the Tavily engine and executes via 
                        the tool that resides in the class <i>dev.langchain4j.web.search.WebSearchTool</i>.</li>
                    <li>Tavily returns the relevant articles.</li>
                    <li>The model extracts an appropriate answer from the articles.</li>
                </ol>
            </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);