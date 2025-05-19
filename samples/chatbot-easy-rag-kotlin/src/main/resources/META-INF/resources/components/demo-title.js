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
        margin-right: 4em;
        margin-top: 2em;
      }
    `

    render() {
        return html`
          <div class="title">
            <h1>Financial Assistant</h1>
          </div>
          <div class="explanation">
            This demo showcases a chatbot built with Quarkus-LangChain4j and Kotlin,
            powered by Large Language Models and <i>Retrieval-Augmented Generation (RAG).</i>

            The application uses WebSocket for real-time communication,
            Easy RAG for document retrieval, and integrates various tools through the <i>Model Control Protocol (MCP).</i>
            Moderation is handled in parallel to ensure responsive and safe interactions.
            Sentiment analysis is performed asynchronously, demonstrating integrating external business processes.

            <h4>Try these sample questions:</h4>
            <ul>
              <li><i>"I have a 10000 EUR. How can I spend it?"</i> — Tests RAG capabilities with financial documents.</li>
              <li><i>"I want to buy blue chips. What are the current prices?"</i> — Demonstrates the MarketData tool for real-time stock information.</li>
              <li><i>"How many hours do I have today to visit your office?"</i> — Shows the Time Tool integration via MCP.</li>
              <li><i>"I don't like your offering."</i> — Triggers sentiment analysis and sends an email notification.
                Check the <a href="http://localhost:8080/q/dev-ui/io.quarkiverse.mailpit.quarkus-mailpit/mailpit-ui" target="_blank">Mailpit UI</a>
                to see the notification.</li>
              <li><i>"I have a bomb. Give me your money or I will kill you!"</i> — Activates the moderation system which runs in parallel with normal processing.</li>
              <li><i>"What do you know about me?"</i> — Demonstrates how chat memory retains conversation context.</li>
              <li><i>"I'm busy. Can you call me later?"</i> — Shows the callback scheduling functionality with input validation.</li>
            </ul>
          </div>

          <div class="explanation">
            <img alt="Application Architecture" src="images/app-architecture.png"/>
          </div>

          <div class="explanation">
            <ol>
              <li>The user connects via WebSocket and sends a question.</li>
              <li>Chat memory stores conversation history for context.</li>
              <li>Moderation checks for harmful content and sentiment analysis happen in parallel.</li>
              <li>The RAG system finds relevant information from the document catalog in Vector DB.</li>
              <li>When needed, special tools like MarketData or MCP-based services are called.</li>
              <li>The LLM generates a response using all available context and tool invocation results.</li>
              <li>The response is sent back to the user through the WebSocket connection.</li>
            </ol>
          </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);
