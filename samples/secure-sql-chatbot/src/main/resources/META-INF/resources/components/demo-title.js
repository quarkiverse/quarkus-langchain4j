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
                <h1>Secure Movie Muse</h1>
            </div>
            <div class="explanation">
                This demo shows how to build a chatbot powered by GPT 3.5 and advanced retrieval augmented generation.
                The application ingests a CSV file listing the top 100 movies from IMDB.
                You can now ask questions about the movies and the application will answer.
                For example <em>What is the rating of the movie Inception?</em> or <em>What comedy movies are in the database?</em>
            </div>
            
            <div class="explanation">
                <img src="images/chatbot-architecture.png"/>
            </div>
            
            <div class="explanation">
                <ol>
                    You must login with Google first before you can interact with the chatbot
                </ol>
            </div>
            
            <div class="explanation">
                <table>
                   <tr>
                     <td><img src="images/google.png"/><td/><td><a href="login">Login with Google</a></td>
                   </tr>
                 </table>
            </div>
        `
    }
}

customElements.define('demo-title', DemoTitle);