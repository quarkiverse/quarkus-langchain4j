import {LitElement, html, css} from 'lit';

export class DemoTitle extends LitElement {

    static styles = css`
      h1 {
        font-size: 40px;
        font-style: normal;
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
        font-size: 16px;
      }
    `

    render() {
        return html`
            <div class="title">
                <h1>Apicurio Registry MCP Discovery</h1>
            </div>
            <div class="explanation">
                This demo shows how to dynamically discover and connect to MCP servers
                registered in an Apicurio Registry instance. Ask the bot to search for
                MCP servers, connect to them, and use their tools.
            </div>
        `
    }
}

customElements.define('demo-title', DemoTitle);
