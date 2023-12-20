import { LitElement, html} from 'lit';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/button';
import '@vaadin/text-field';
import '@vaadin/text-area';
import '@vaadin/grid';
import 'qui-alert';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import '@vaadin/grid/vaadin-grid-sort-column.js';


export class QwcEmbeddingStore extends LitElement {

    jsonRpc = new JsonRpc(this);

    static properties = {
        "_addEmbeddingConfirmation": {state: true},
        "_relevantEmbeddingsOutput": {state: true}
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
        this._addEmbeddingConfirmation = html`Working...<br/>`;
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
        this._relevantEmbeddingsOutput = html`Working...<br/>`;
        this.jsonRpc.findRelevant({text: text, limit: limit}).then(jsonRpcResponse => {
            this._relevantEmbeddingsOutput = html`
                <vaadin-grid id="relevant-embeddings" .items=${jsonRpcResponse.result}>
                    <vaadin-grid-sort-column path="embeddingId" header="ID" ${columnBodyRenderer(this._embeddingMatchIdRenderer, [])}></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="score" header="Score" ${columnBodyRenderer(this._embeddingMatchScoreRenderer, [])}></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="embedded" header="Text segment" ${columnBodyRenderer(this._embeddingMatchEmbeddedRenderer, [])}></vaadin-grid-sort-column>
                    <vaadin-grid-sort-column path="metadata" header="Metadata" ${columnBodyRenderer(this._embeddingMatchMetadataRenderer, [])}></vaadin-grid-sort-column>
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
        if (match.metadata && match.metadata.length > 0) {
            return html`<vaadin-vertical-layout>
                          ${match.metadata.map((entry) =>
                html`<div><code>${entry.key}:${entry.value}</code></div>`
            )}</vaadin-vertical-layout>`;
        }
    }


}
customElements.define('qwc-embedding-store', QwcEmbeddingStore);