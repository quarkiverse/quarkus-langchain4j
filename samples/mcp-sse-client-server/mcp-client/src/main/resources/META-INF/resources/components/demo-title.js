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
                <h1>US Weather Assistant</h1>
            </div>
            <div class="explanation">
                This sample demonstrates the usage of MCP Clients and Servers with Quarkus
                 to allow the LLM to interact with a Quarkus MCP Server.
                The server provides weather related tools for US-based locations.
                
                Suggested prompts to try out:
                <ul>
                    <li>What is the weather like in Salt Lake City?</li>
                    <li>What about Missoula, MT?</i></li>
                    <li>Are there any weather alerts for the state of California?</li>
                </ul>
            </div>
        `
    }

}

customElements.define('demo-title', DemoTitle);