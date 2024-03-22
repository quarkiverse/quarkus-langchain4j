import {LitElement, html, css} from 'lit';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/form-layout';
import '@vaadin/progress-bar';
import '@vaadin/checkbox';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';

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
                <h1>Bank Buddy</h1>
            </div>
            <div class="explanation">
                This demo shows how to build a chat bot powered by GPT 3.5 and retrieval augmented generation.
                The description of the different accounts is ingested into a Redis database and relevant information
                is sent to the LLM before answering the user.
            </div>
            
            <div class="explanation">
                <img src="images/chatbot-architecture.png"/>
            </div>
            
            <div class="explanation">
                <ol>
                    <li>The user send a question to the application.</li>
                    <li>The application looks for relevant data in the Redis store.</li>
                    <li>The relevant data is retrieved and added to the user's question.</li>
                    <li>The extended question is sent to the LLM model.</li>
                    <li>The response is received and sent back to the user.</li>
                </ol>
            </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);