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
                <h1>Filesystem assistant</h1>
            </div>
            <div class="explanation">
                This sample demonstrates the usage of MCP servers 
                (<i>@modelcontextprotocol/server-filesystem</i> in particular) to allow
                the LLM to interact with the filesystem of the host machine.
                
                The only directory that the agent has access to is the <i>playground</i>
                directory relative to this project's root directory, so all relative paths
                that you provide will be resolved against that directory.
                
                Suggested prompts to try out:
                <ul>
                    <li>Read the contents of the file hello.txt</li>
                    <li>Read the contents of the file hello2.txt <i>(NOTE: this file does not exist)</i></li>
                    <li>Write a python script that takes two integer arguments and prints their sum, and then save it as adder.py</li>
                </ul>
            </div>
        `
    }


}

customElements.define('demo-title', DemoTitle);