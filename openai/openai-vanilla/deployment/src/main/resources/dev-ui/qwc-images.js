import {html, LitElement} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/text-area';
import '@vaadin/button';
import { JsonRpc } from 'jsonrpc';

import {imageModelConfigurations} from 'build-time-data';

export class QwcImages extends LitElement {

    jsonRpc = new JsonRpc(this);

    supportedModels = [
        { label: "dall-e-2",  value: "dall-e-2"},
        { label: "dall-e-3",  value: "dall-e-3"}]

    supportedSizes = [
        { label: "256x256",  value: "256x256"},
        { label: "512x512",  value: "512x512"},
        { label: "1024x1024",  value: "1024x1024"},
        { label: "1024x1792",  value: "1024x1792"},
        { label: "1792x1024",  value: "1792x1024"}
    ]

    supportedQualities = [
        { label: "standard",  value: "standard"},
        { label: "hd",  value: "hd"}]

    supportedStyles = [
        { label: "vivid",  value: "vivid"},
        { label: "natural",  value: "natural"}]

    static properties = {
        "_generatedImages": {state: true},
        "_statusInfo": {state: true},
        "_imageModelConfigurations": {state: true},
    }

    constructor() {
        super();
        this._generatedImages = [];
        this._imageModelConfigurations = [];
        imageModelConfigurations.forEach((config) => {
            this._imageModelConfigurations = this._imageModelConfigurations.concat([{label: config, value: config}]);
        })
    }

    render() {
        return html`
            <h3>Model configuration</h3>
            <vaadin-horizontal-layout>
                <vaadin-select
                        label="Model configuration"
                        helper-text="Name of the configured OpenAI client (this corresponds to NAME 
                        in \`quarkus.langchain4j.openai.NAME.*\` properties)."
                        id="model-configuration"
                        .items="${this._imageModelConfigurations}"
                        .value="${this._imageModelConfigurations[0].value}"
                ></vaadin-select>
                <vaadin-select
                        label="Model"
                        id="model-name"
                        .items="${this.supportedModels}"
                        .value="${this.supportedModels[0].value}"
                ></vaadin-select>
                <vaadin-select
                        label="Size"
                        helper-text="Must be one of 1024x1024, 1792x1024, or 1024x1792 when the model is dall-e-3. 
                        Must be one of 256x256, 512x512, or 1024x1024 when the model is dall-e-2."
                        id="size"
                        .items="${this.supportedSizes}"
                        .value="${this.supportedSizes[0].value}"
                ></vaadin-select>
                <vaadin-select
                        label="Quality"
                        helper-text="The quality of the image that will be generated.
                        'hd' creates images with finer details and greater consistency across the image.
                        This param is only supported for when the model is dall-e-3."
                        id="quality"
                        .items="${this.supportedQualities}"
                        .value="${this.supportedQualities[0].value}"
                ></vaadin-select>
                <vaadin-select
                        label="Style"
                        helper-text="Vivid causes the model to lean towards generating hyper-real and dramatic images.
                        Natural causes the model to produce more natural, less hyper-real looking images. 
                        This param is only supported for when the model is dall-e-3."
                        id="style"
                        .items="${this.supportedStyles}"
                        .value="${this.supportedStyles[0].value}"
                ></vaadin-select>
            </vaadin-horizontal-layout>
            <vaadin-text-area id="image-prompt" label="Prompt" style="width:90%"></vaadin-text-area><br/>
            ${this._statusInfo}
            <vaadin-button id="image-submit" @click=${() => this._doGenerate(
                    this.shadowRoot.getElementById('model-configuration').value,
                    this.shadowRoot.getElementById('model-name').value,
                    this.shadowRoot.getElementById('image-prompt').value,
                    this.shadowRoot.getElementById('size').value,
                    this.shadowRoot.getElementById('quality').value,
                    this.shadowRoot.getElementById('style').value
            )}>Generate image
            </vaadin-button>
            <br/>
            <h3>Generated images</h3>
            ${this._renderImages()}
        `;
    }

    _doGenerate(configuration, modelName, prompt, size, quality, style) {
        this._statusInfo = html`Generating image...<br/>`;
        this.jsonRpc.generate({configuration: configuration, modelName: modelName,
                prompt: prompt, size: size, quality: quality, style: style}).then((jsonRpcResponse) => {
            this._statusInfo = html`<qui-alert level="success" showIcon>
                    <span>Image generated successfully.</span>
                </qui-alert>`;
            this._generatedImages = [jsonRpcResponse.result].concat(this._generatedImages)
        }).catch((error) => {
            this._statusInfo = html`
                <qui-alert level="error" showIcon>
                    <span>${JSON.stringify(error.error)}</span>
                </qui-alert>`
        });
    }

    _renderImages() {
        if(this._generatedImages.length === 0) {
            return html`Nothing yet`;
        } else {
            return html`
                ${this._generatedImages.map((image) => {
                            return html`
                                <img src="${image.url ? image.url : "data:image/png;base64," + image.base64Data}"
                                     alt="${image.prompt}" style="max-width: 100%; max-height: 100%;"/><br/>
                                <span>${image.prompt}</span><br/>
                            `;
                        }
                )}
            `;
        }
    }

}

customElements.define('qwc-images', QwcImages);