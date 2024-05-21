import {LitElement, html, css} from 'lit';
import '@vaadin/horizontal-layout';
import '@vaadin/vertical-layout';
import '@vaadin/tooltip';
import '@vaadin/text-area';
import '@vaadin/button';
import '@vaadin/progress-bar';
import { notifier } from 'notifier';

import { JsonRpc } from 'jsonrpc';

import {imageModelConfigurations} from 'build-time-data';

export class QwcImages extends LitElement {

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

        .images {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
        }

        .image {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 5px;
        }
        .hide {
            visibility: hidden;
        }
        .show {
            visibility: visible;
        }  
    `;

    supportedModels = [
        { label: "dall-e-2",  value: "dall-e-2"},
        { label: "dall-e-3",  value: "dall-e-3"}]

    supportedSizes = [
        { label: "256x256",  value: "256x256", supportedModels: ["dall-e-2"]},
        { label: "512x512",  value: "512x512", supportedModels: ["dall-e-2"]},
        { label: "1024x1024",  value: "1024x1024", supportedModels: ["dall-e-2", "dall-e-3"]},
        { label: "1792x1024",  value: "1792x1024", supportedModels: ["dall-e-3"]},
        { label: "1024x1792",  value: "1024x1792", supportedModels: ["dall-e-3"]},
        { label: "1792x1024",  value: "1792x1024", supportedModels: ["dall-e-3"]}
    ]

    supportedQualities = [
        { label: "standard",  value: "standard", supportedModels: ["dall-e-2", "dall-e-3"]},
        { label: "hd",  value: "hd", supportedModels: ["dall-e-3"]}]

    supportedStyles = [
        { label: "vivid",  value: "vivid", supportedModels: ["dall-e-2", "dall-e-3"]},
        { label: "natural",  value: "natural", supportedModels: ["dall-e-3"]}]

    static properties = {
        _progressBarClass: {state: true},
        _generatedImages: {state: true},
        _imageModelConfigurations: {state: true},
        selectedModel: { state: true },
    }

    constructor() {
        super();
        this._hideProgressBar();

        this._generatedImages = [];
        this._imageModelConfigurations = [];
        imageModelConfigurations.forEach((config) => {
            this._imageModelConfigurations = this._imageModelConfigurations.concat([{label: config, value: config}]);
        })
       
        this.selectedModel = this.supportedModels[0].value;
    }

    render() {
        return html`
                    ${this._renderConfig()}
                    ${this._renderMain()}
        `;
    }

    _onModelChange(e) {
        this.selectedModel = e.target.value;

        const availableSizes = this._getSupportedSizesForModel(this.selectedModel);
        const availableQualities = this._getSupportedQualitiesForModel(this.selectedModel);
        const availableStyles = this._getSupportedStylesForModel(this.selectedModel);
        
        this.selectedSize = this._getSupportedSizesForModel(this.selectedModel)[0].value;
        this.selectedQuality = this._getSupportedQualitiesForModel(this.selectedModel)[0].value;
        this.selectedStyle = this._getSupportedStylesForModel(this.selectedModel)[0].value;
       
        // Reset the values if the selected model does not support the current value
        if (!availableSizes.map(s => s.value).includes(this.shadowRoot.getElementById('size').value)) {
          this.shadowRoot.getElementById('size').value = availableSizes[0].value;
        }
        
        if (!availableQualities.map(s => s.value).includes(this.shadowRoot.getElementById('quality').value)) {
          this.shadowRoot.getElementById('quality').value = availableQualities[0].value;
        }
        
        if (!availableStyles.map(s => s.value).includes(this.shadowRoot.getElementById('style').value)) {
          this.shadowRoot.getElementById('style').value = availableStyles[0].value;
        }
    }
     
    _getSupportedSizesForModel(model) {
        return this.supportedSizes.filter(size => size.supportedModels.includes(model));
    }

    _getSupportedQualitiesForModel(model) {
        return this.supportedQualities.filter(quality => quality.supportedModels.includes(model));
    }
    
    _getSupportedStylesForModel(model) {
        return this.supportedStyles.filter(style => style.supportedModels.includes(model));
    }

    _renderConfig() {
        return html`
            <div class="config">
                <vaadin-vertical-layout>
                    <vaadin-select
                            label="Model configuration"
                            id="model-configuration"
                            .items="${this._imageModelConfigurations}"
                            .value="${this._imageModelConfigurations[0].value}">
                            <vaadin-tooltip slot="tooltip" text="Name of the configured OpenAI client (this corresponds to NAME 
                                in \`quarkus.langchain4j.openai.NAME.*\` properties)." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                    <vaadin-select
                            label="Model"
                            id="model-name"
                            .items="${this.supportedModels}"
                            .value="${this.supportedModels[0].value}"
                            @change="${this._onModelChange}">
                    </vaadin-select>
                    <vaadin-select
                            label="Size"
                            id="size"
                            .items="${this._getSupportedSizesForModel(this.selectedModel) }"
                            .value="${this._getSupportedSizesForModel(this.selectedModel)[0].value}">
                            <vaadin-tooltip slot="tooltip" text="Must be one of 1024x1024, 1792x1024, or 1024x1792 when the model is dall-e-3. 
                                Must be one of 256x256, 512x512, or 1024x1024 when the model is dall-e-2." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                    <vaadin-select
                            label="Quality"
                            id="quality"
                            .items="${this._getSupportedQualitiesForModel(this.selectedModel) }"
                            .value="${this._getSupportedQualitiesForModel(this.selectedModel)[0].value}">
                            <vaadin-tooltip slot="tooltip" text="The quality of the image that will be generated.
                                'hd' creates images with finer details and greater consistency across the image.
                                This param is only supported for when the model is dall-e-3." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                    <vaadin-select
                            label="Style"
                            id="style"
                            .items="${this._getSupportedStylesForModel(this.selectedModel) }"
                            .value="${this._getSupportedStylesForModel(this.selectedModel)[0].value}">
                            <vaadin-tooltip slot="tooltip" text="Vivid causes the model to lean towards generating hyper-real and dramatic images.
                                Natural causes the model to produce more natural, less hyper-real looking images. 
                                This param is only supported for when the model is dall-e-3." position="bottom"></vaadin-tooltip>
                    </vaadin-select>
                </vaadin-vertical-layout>
            </div>
        `;
    }

    _renderMain(){
        return html`
        <div class="main">
            <div class="input">
                <vaadin-text-area id="image-prompt" label="Prompt" style="width:100%"></vaadin-text-area>
                <div class="inputAction">
                    <vaadin-button id="image-submit" @click=${() => this._doGenerate(
                            this.shadowRoot.getElementById('model-configuration').value,
                            this.shadowRoot.getElementById('model-name').value,
                            this.shadowRoot.getElementById('image-prompt').value,
                            this.shadowRoot.getElementById('size').value,
                            this.shadowRoot.getElementById('quality').value,
                            this.shadowRoot.getElementById('style').value
                        )}>Generate image
                    </vaadin-button>
                    <vaadin-progress-bar class="${this._progressBarClass}" indeterminate></vaadin-progress-bar>
                </div>
            </div>
            <div class="output">
                ${this._renderImages()}
            </div>
        </div>
        `;
    }

    _doGenerate(configuration, modelName, prompt, size, quality, style) {
        this._showProgressBar();
        this.jsonRpc.generate({configuration: configuration, modelName: modelName,
                prompt: prompt, size: size, quality: quality, style: style}).then((jsonRpcResponse) => {
                    this._hideProgressBar();
                    this._generatedImages = [jsonRpcResponse.result].concat(this._generatedImages)
        }).catch((error) => {
            this._hideProgressBar();
            notifier.showErrorMessage(JSON.stringify(error.error));
        });
    }

    _renderImages() {
        if(this._generatedImages.length !== 0) {
            return html`<div class="images">
                ${this._generatedImages.map((image) => {
                            return html`<div class="image">
                                <img src="${image.url ? image.url : "data:image/png;base64," + image.base64Data}"
                                     alt="${image.prompt}" style="max-width: 100%; max-height: 100%;"/>
                                <span>${image.prompt}</span>
                                </div>
                            `;
                        }
                )}
            </div>`;
        }
    }
    _hideProgressBar(){
        this._progressBarClass = "hide";
    }

    _showProgressBar(){
        this._progressBarClass = "show";
    }

}

customElements.define('qwc-images', QwcImages);
