import { LitElement, html} from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';

import {tools} from 'build-time-data';


export class QwcTools extends LitElement {

    static properties = {
        "_tools": {state: true},
    }

    constructor() {
        super();
        this._tools = tools;
    }

    render() {
        if (this._tools) {
            return this._renderToolTable();
        } else {
            return html`<span>No tools found</span>`;
        }
    }

    _renderToolTable() {
        return html`
                <vaadin-grid .items="${this._tools}" theme="no-border">
                    <vaadin-grid-sort-column auto-width
                                        path="className"
                                        header="Class name">
                    </vaadin-grid-sort-column>
                    <vaadin-grid-column auto-width
                                        path="name"
                                        header="Tool name">
                    </vaadin-grid-column>
                    <vaadin-grid-column auto-width
                                        path="description"
                                        header="Description">
                    </vaadin-grid-column>
                </vaadin-grid>`;
    }

}
customElements.define('qwc-tools', QwcTools);