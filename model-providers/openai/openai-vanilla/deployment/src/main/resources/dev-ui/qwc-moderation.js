import {LitElement, html, css} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/tooltip';
import '@vaadin/progress-bar';
import { notifier } from 'notifier';
import { JsonRpc } from 'jsonrpc';

import {moderationModelConfigurations} from 'build-time-data';

export class QwcModerationModels extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            gap: 20px;
            margin-left: 15px;
            margin-right: 15px;
        }
        .config {
            background: var(--lumo-contrast-5pct);
            padding: 5px;
        }
        .main {
            width: 100%;
            padding: 5px;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .inputAction {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        .hide {
            visibility: hidden;
        }
        .show {
            visibility: visible;
        } 
        `;

    supportedModels = [
        { label: "omni-moderation-latest",  value: "omni-moderation-latest"}
    ]

    static properties = {
        _moderationResponse: {state: true},
        _progressBarClass: {state: true},
    }

    constructor() {
        super();
        this._hideProgressBar();
        this._moderationModelConfigurations = [];
        moderationModelConfigurations.forEach((config) => {
            this._moderationModelConfigurations = this._moderationModelConfigurations.concat([{label: config, value: config}]);
        })
    }

    render() {
        return html`
            ${this._renderConfig()}
            ${this._renderMain()}
        `;
    }

    _renderConfig(){
        return html`
            <div class="config">
                <vaadin-vertical-layout>
                    <vaadin-select
                            label="Model configuration"
                            id="configuration"
                            .items="${this._moderationModelConfigurations}"
                            .value="${this._moderationModelConfigurations[0].value}">
                            <vaadin-tooltip slot="tooltip" text="Name of the OpenAI configuration to use (this corresponds to NAME 
                            in \`quarkus.langchain4j.openai.NAME.*\` properties)." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                    <vaadin-select
                            label="Model"
                            id="model-name"
                            .items="${this.supportedModels}"
                            .value="${this.supportedModels[0].value}">
                            <vaadin-tooltip slot="tooltip" text="Allows to override the moderation model to use (this corresponds to 
                            the \`quarkus.langchain4j.openai.NAME.moderation-model.model-name\` property)." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                </vaadin-vertical-layout>
            </div>
        `;
    }

    _renderMain(){
        return html`
        <div class="main">
            <div class="input">
                <vaadin-text-area id="prompt" label="Prompt" style="width:100%"></vaadin-text-area>
                <div class="inputAction">
                    <vaadin-button id="image-submit" @click=${() => this._doGenerate(
                            this.shadowRoot.getElementById('configuration').value,
                            this.shadowRoot.getElementById('model-name').value,
                            this.shadowRoot.getElementById('prompt').value
                        )}> Moderate the prompt
                    </vaadin-button>
                    <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
                </div>
            </div>
            <div class="output">
                ${this._moderationResponse}    
            </div>
        </div>`;
    }

    _doGenerate(configuration, modelName, prompt) {
        this._showProgressBar();
        this.jsonRpc.moderate({configuration: configuration, modelName: modelName, prompt: prompt}).then((jsonRpcResponse) => {
            this._hideProgressBar();

            this._moderationResponse = this._printResponse(jsonRpcResponse.result);
            
        }).catch((error) => {
            this._hideProgressBar();
            notifier.showErrorMessage(JSON.stringify(error.error));
        });
    }

    _printResponse(response) {
        if (response) {
            return html`
                <span>Flagged: ${response.flagged}</span>
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

    _hideProgressBar(){
        this._progressBarClass = "hide";
    }

    _showProgressBar(){
        this._progressBarClass = "show";
    }
}

customElements.define('qwc-moderation', QwcModerationModels);
