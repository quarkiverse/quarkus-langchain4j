import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/form-layout';
import '@vaadin/progress-bar';
import '@vaadin/checkbox';
import '@vaadin/grid';
import 'qui-alert';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';


export class QwcEmbeddingStore extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        .button {
            cursor: pointer;
        }
        .clearIcon {
            color: orange;
        }
        .message {
          padding: 15px;
          text-align: center;
          margin-left: 20%;
          margin-right: 20%;
          border: 2px solid orange;
          border-radius: 10px;
          font-size: large;
        }
        `;

    static properties = {
        "_addEmbeddingConfirmation": {state: true},
        "_relevantEmbeddingsOutput": {state: true}
    }

    connectedCallback() {
        super.connectedCallback();
    }

    render() {
            return html`
                <h3>Add a new embedding</h3>
                ${this._addEmbeddingConfirmation}
                <vaadin-text-area id="embedding-text" label="Text segment"></vaadin-text-area><br/>
                <vaadin-text-area id="embedding-id" label="(Optional) Embedding ID"></vaadin-text-area><br/>
                <vaadin-text-area id="metadata"
                                  helper-text="Key-value pairs separated by commas or line breaks"
                                  pattern="^(([a-zA-Z0-9_]+=[a-zA-Z0-9_]+)(,|\\n))*([a-zA-Z0-9_]+=[a-zA-Z0-9_]+)$"
                                  label="(Optional) Metadata"></vaadin-text-area><br/>
                <vaadin-button @click=${() => this._addEmbedding(
                    this.shadowRoot.getElementById('embedding-id').value,
                    this.shadowRoot.getElementById('embedding-text').value,
                    this.shadowRoot.getElementById('metadata').value
                )}>Create and store</vaadin-button>
                
                <h3>Search for relevant embeddings</h3>
                <vaadin-text-area id="search-text" label="Search text"></vaadin-text-area><br/>
                <vaadin-text-field id="search-limit" label="Limit" value="10"></vaadin-text-field><br/>
                <vaadin-button @click=${() => this._findRelevant(
                        this.shadowRoot.getElementById('search-text').value,
                        this.shadowRoot.getElementById('search-limit').value
                )}>Search</vaadin-button><br/>
                ${this._relevantEmbeddingsOutput}
            `;
    }

    _addEmbedding(id, text, metadata){
        this._addEmbeddingConfirmation = '';
        this.jsonRpc.add({id: id, text: text, metadata: metadata}).then(jsonRpcResponse => {
            if(jsonRpcResponse.result === false) {
                this._addEmbeddingConfirmation = html`
                    <qui-alert level="error" showIcon>
                        <span>The embedding could not be added: ${jsonRpcResponse}</span>
                    </qui-alert>`;
            } else {
                this._addEmbeddingConfirmation = html`
                    <qui-alert level="success" showIcon>
                        <span>The embedding was added with ID <code>${jsonRpcResponse.result}</code>.</span>
                    </qui-alert>`;
            }
        });
    }

    _findRelevant(text, limit){
        this._relevantEmbeddingsOutput = '';
        this.jsonRpc.findRelevant({text: text, limit: limit}).then(jsonRpcResponse => {
            this._relevantEmbeddingsOutput = html`
                <vaadin-grid id="relevant-embeddings" .items=${jsonRpcResponse.result} class="datatable">
                    <vaadin-grid-column path="embeddingId" header="ID" ${columnBodyRenderer(this._embeddingMatchIdRenderer, [])}></vaadin-grid-column>
                    <vaadin-grid-column path="score" header="Score" ${columnBodyRenderer(this._embeddingMatchScoreRenderer, [])}></vaadin-grid-column>
                    <vaadin-grid-column path="embedded" header="Text segment" ${columnBodyRenderer(this._embeddingMatchEmbeddedRenderer, [])}></vaadin-grid-column>
                    <vaadin-grid-column path="metadata" header="Metadata" ${columnBodyRenderer(this._embeddingMatchMetadataRenderer, [])}></vaadin-grid-column>
                </vaadin-grid>
                `;
        });
    }

    _embeddingMatchIdRenderer(match) {
        return html`${ match.embeddingId }`
    }

    _embeddingMatchScoreRenderer(match) {
        return html`${ match.score }`
    }

    _embeddingMatchEmbeddedRenderer(match) {
        return html`${ match.embedded }`
    }

    _embeddingMatchMetadataRenderer(match) {
        // return html`${ match.metadata }`
        if (match.metadata && match.metadata.length > 0) {
            return html`<vaadin-vertical-layout>
                          ${match.metadata.map((entry) =>
                html`<div><code>${entry.key}:${entry.value}</code></div>`
            )}</vaadin-vertical-layout>`;
        }
    }


}
customElements.define('qwc-embedding-store', QwcEmbeddingStore);