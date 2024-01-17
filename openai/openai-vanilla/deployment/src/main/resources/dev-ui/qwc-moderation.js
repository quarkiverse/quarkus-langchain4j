import {html, LitElement} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/text-area';
import '@vaadin/button';
import { JsonRpc } from 'jsonrpc';

export class QwcModerationModels extends LitElement {

    jsonRpc = new JsonRpc(this);

    supportedModels = [
        { label: "text-moderation-latest",  value: "text-moderation-latest"},
        { label: "text-moderation-stable",  value: "text-moderation-stable"}]

    static properties = {
        "_moderationResponse": {state: true}
    }

    constructor() {
        super();
    }

    render() {
        return html`
            <h3>Moderation model</h3>
            <vaadin-horizontal-layout>
                <vaadin-select
                        label="Model"
                        id="model-name"
                        .items="${this.supportedModels}"
                        .value="${this.supportedModels[0].value}"
                ></vaadin-select>
            </vaadin-horizontal-layout>
            <vaadin-text-area id="prompt" label="Prompt" style="width:90%"></vaadin-text-area><br/>
            
            <vaadin-button id="image-submit" @click=${() => this._doGenerate(
                    this.shadowRoot.getElementById('model-name').value,
                    this.shadowRoot.getElementById('prompt').value
            )}>Moderate the prompt
            </vaadin-button>
            <br/>
            <h3>Moderation response</h3>
            ${this._moderationResponse}
        `;
    }

    _doGenerate(modelName, prompt) {
        this._moderationResponse = html`Retrieving...<br/>`;
        this.jsonRpc.moderate({modelName: modelName, prompt: prompt}).then((jsonRpcResponse) => {
            this._moderationResponse = this._printResponse(jsonRpcResponse.result);
        }).catch((error) => {
            this._moderationResponse = html`
                <qui-alert level="error" showIcon>
                    <span>${JSON.stringify(error.error)}</span>
                </qui-alert>`
        });
    }

    _printResponse(response) {
        if (response) {
            return html`
                <span>Flagged: ${response.flagged}</span><br/>
                <vaadin-grid .items="${response.categories}" theme="no-border">
                    <vaadin-grid-sort-column auto-width
                                             path="name"
                                             header="Category">
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column auto-width
                                             path="flagged"
                                             header="Flagged">
                    </vaadin-grid-sort-column>
                    <vaadin-grid-sort-column auto-width
                                             path="score"
                                             header="Score">
                    </vaadin-grid-sort-column>
                </vaadin-grid>`;
        } else {
            return html``;
        }

    }

}

customElements.define('qwc-moderation', QwcModerationModels);