import { LitElement, html, css} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/grid';
import '@vaadin/progress-bar';
import 'qui-alert';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';


export class QwcEmbeddingStore extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            height: 100%;
            display: flex;
	    flex-direction: column;
        }
    `;

    static properties = {
        "_addEmbeddingConfirmation": {state: true},
        "_relevantEmbeddingsOutput": {state: true}
    }

    render() {
            return html`
                <h3>Add a new embedding</h3>
                <vaadin-text-area id="embedding-text" label="Text segment" required min-length="1"></vaadin-text-area><br/>
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
                ${this._addEmbeddingConfirmation}
                
                <h3>Search for relevant embeddings</h3>
                <vaadin-text-area id="search-text" label="Search text" required min-length="1"></vaadin-text-area><br/>
                <vaadin-text-field id="search-limit" label="Limit" value="10" required min-length="1"></vaadin-text-field><br/>
                <vaadin-button @click=${() => this._findRelevant(
                        this.shadowRoot.getElementById('search-text').value,
                        this.shadowRoot.getElementById('search-limit').value
                )}>Search</vaadin-button><br/>
                ${this._relevantEmbeddingsOutput}
            `;
    }

    _addEmbedding(id, text, metadata) {
        this._addEmbeddingConfirmation = html`<vaadin-progress-bar class="show" indeterminate></vaadin-progress-bar>
`;
        this.jsonRpc.add({id: id, text: text, metadata: metadata}).then(jsonRpcResponse => {
            this._addEmbeddingConfirmation = html`
                <qui-alert level="success" showIcon>
                    <span>The embedding was added with ID <code>${jsonRpcResponse.result}</code>.</span>
                </qui-alert>`;
        }).catch((error) => {
            this._addEmbeddingConfirmation = html`
                <qui-alert level="error" showIcon>
                    <span>${JSON.stringify(error.error)}</span>
                </qui-alert>`;
        });
    }

    _findRelevant(text, limit){
        this._relevantEmbeddingsOutput = html`<vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>`;
        this.jsonRpc.findRelevant({text: text, limit: limit}).then(jsonRpcResponse => {
            this._relevantEmbeddingsOutput = html`
                <vaadin-grid  theme="wrap-cell-content" id="relevant-embeddings" .items=${jsonRpcResponse.result}>
                    <vaadin-grid-sort-column path="embeddingId" header="ID" resizable></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="score" header="Score" resizable></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="embedded" header="Text segment" resizable></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column header="Metadata" resizable ${columnBodyRenderer(this._embeddingMatchMetadataRenderer, [])}></vaadin-grid-sort-column>
                </vaadin-grid>
                `;
        }).catch((error) => {
            this._relevantEmbeddingsOutput = html`
                <qui-alert level="error" showIcon>
                    <span>${JSON.stringify(error.error)}</span>
                </qui-alert>`
        });
    }

    _embeddingMatchMetadataRenderer(match) {
        if (match.metadata && match.metadata.length > 0) {
            return html`<vaadin-vertical-layout>
                          ${match.metadata.map((entry) =>
                html`<div><code>${entry.key}:${entry.value}</code></div>`
            )}</vaadin-vertical-layout>`;
        }
    }


}
customElements.define('qwc-embedding-store', QwcEmbeddingStore);
