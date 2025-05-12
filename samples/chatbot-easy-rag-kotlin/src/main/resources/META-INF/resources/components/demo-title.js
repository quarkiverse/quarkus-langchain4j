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
        float: left;
        margin-right: 2em;
        margin-top: 1em;
      }
    `

    render() {
        return html`
            <div class="title">
                <h1>Financial Assistant</h1>
            </div>
            <div class="explanation">
                This demo shows how to build a chat bot powered by LLM and retrieval-augmented generation (RAG).
                The application uses the Easy RAG extension to retrieve information about financial products from provided
                documents stored in a catalog directory.
                The description of the different accounts is ingested into a Redis database, and relevant information
                is sent to the LLM before answering the user. It also provides stock price information for selected companies.

                Here are some examples of questions that you can ask:
                <ul>
                  <li>"What is the minimum opening deposit for the standard savings account?" — The Agent is aware
                    of characteristics of financial products</li>
                  <li>"What is the current stock price of GOOG?" — The Agent uses Tools to retrieve the data,
                    but in this example it generates random numbers.</li>
                  <li>"I am not happy to use your products" will trigger negative sentiment detection.
                    You may have a look at <a href="http://localhost:8080/q/dev-ui/io.quarkiverse.mailpit.quarkus-mailpit/mailpit-ui" target="_blank">Mailpit UI</a>
                    afterwards to find the email about it.</li>
                  <li>"You are so stupid!" will trigger moderation</li>
                </ul>
            </div>

            <div class="explanation">
                <img alt="" src="images/chatbot-architecture.png"/>
            </div>

            <div class="explanation">
                <ol>
                    <li>The user sends a question to the application.</li>
                    <li>The application looks for relevant data in the document catalog.</li>
                    <li>The relevant data is retrieved and added to the user's question.</li>
                    <li>The extended question is sent to the LLM model.</li>
                    <li>The response is received and sent back to the user.</li>
                    <li>If malicious content is detected in a user message, then an email notification is sent.</li>
                </ol>
            </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);
