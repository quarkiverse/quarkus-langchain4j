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
        width: 90%;
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
              This demo showcases a chat bot powered by Large Language Models (LLM) and Retrieval-Augmented Generation (RAG).
              The application leverages the Easy RAG extension to retrieve information about financial products from documents
              stored in a catalog directory.
              Financial account descriptions are ingested into a Redis database, and the system sends relevant information
              to the LLM before generating responses. The bot also provides stock price information for selected companies.

              <h4>Try these sample questions:</h4>
                <ul>
                  <li>"What is the minimum opening deposit for the saving account?", "Suggest wealth management plan"
                    — The Agent provides information about specific financial product features.</li>
                  <li>"What date and time is it in Tokyo now?" — The Agent uses Tools to retrieve current time</li>
                  <li>"What is the current stock price of GOOG?" — The Agent uses Tools to retrieve data
                    (note: in this demo, it generates random numbers).</li>
                  <li>"I have a bunch of money. Got any financial advice?" — The Agent will recommend appropriate financial
                    products or help schedule a consultation for personalized financial advice.</li>
                  <li>"I am not happy to use your products" will trigger negative sentiment detection.
                    You can check the <a href="http://localhost:8080/q/dev-ui/io.quarkiverse.mailpit.quarkus-mailpit/mailpit-ui" target="_blank">Mailpit UI</a>
                    afterward to see the email notification.</li>
                  <li>"You are so stupid!" will trigger the moderation system.</li>
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
