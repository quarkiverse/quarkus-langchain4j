import { LitElement, html, css } from 'lit';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-sort-column.js';
import '@vaadin/text-field';
import '@vaadin/details';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

import { agents } from 'build-time-data';

export class QwcAgents extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
            padding: 15px;
        }
        vaadin-grid {
            flex: 1;
        }
        .filter-bar {
            margin-bottom: 10px;
        }
        .badge {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 0.8em;
            font-weight: bold;
            color: var(--lumo-primary-contrast-color);
        }
        .badge-Agent { background-color: var(--lumo-primary-color); }
        .badge-SequenceAgent { background-color: var(--lumo-success-color); }
        .badge-ParallelAgent { background-color: var(--lumo-warning-color); }
        .badge-ParallelMapperAgent { background-color: var(--lumo-warning-color); }
        .badge-SupervisorAgent { background-color: var(--lumo-primary-color-50pct); }
        .badge-LoopAgent { background-color: var(--lumo-error-color); }
        .badge-ConditionalAgent { background-color: var(--lumo-success-color-50pct); }
        .badge-PlannerAgent { background-color: var(--lumo-contrast-60pct); }
        .badge-HumanInTheLoop { background-color: var(--lumo-contrast-50pct); }
        .badge-A2AClientAgent { background-color: var(--lumo-error-color-50pct); }
        .root-badge {
            display: inline-block;
            padding: 1px 5px;
            border-radius: 3px;
            font-size: 0.7em;
            background-color: var(--lumo-success-color);
            color: var(--lumo-primary-contrast-color);
            margin-left: 5px;
        }
        .detail-section {
            padding: 10px;
            font-size: 0.9em;
        }
        .detail-section dt { font-weight: bold; margin-top: 8px; }
        .detail-section dd { margin-left: 15px; margin-bottom: 4px; }
        .detail-section code {
            background-color: var(--lumo-contrast-10pct);
            padding: 1px 4px;
            border-radius: 3px;
            font-size: 0.9em;
        }
    `;

    static properties = {
        _agents: { state: true },
        _filter: { state: true },
    };

    constructor() {
        super();
        this._agents = agents;
        this._filter = '';
    }

    render() {
        if (!this._agents || this._agents.length === 0) {
            return html`<p>No agents detected.</p>`;
        }

        const filtered = this._agents.filter(a =>
            a.simpleName.toLowerCase().includes(this._filter.toLowerCase()) ||
            a.agentType.toLowerCase().includes(this._filter.toLowerCase()) ||
            a.description.toLowerCase().includes(this._filter.toLowerCase())
        );

        return html`
            <div class="filter-bar">
                <vaadin-text-field
                    placeholder="Filter agents..."
                    clear-button-visible
                    @input="${e => this._filter = e.target.value}">
                </vaadin-text-field>
            </div>
            <vaadin-grid .items="${filtered}" theme="row-stripes wrap-cell-content">
                <vaadin-grid-sort-column auto-width path="simpleName" header="Name"
                    ${columnBodyRenderer(this._nameRenderer, [])}>
                </vaadin-grid-sort-column>
                <vaadin-grid-sort-column auto-width path="agentType" header="Type"
                    ${columnBodyRenderer(this._typeRenderer, [])}>
                </vaadin-grid-sort-column>
                <vaadin-grid-column auto-width header="Description"
                    ${columnBodyRenderer(this._descriptionRenderer, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column auto-width header="Output Key" path="outputKey">
                </vaadin-grid-column>
                <vaadin-grid-column auto-width header="Sub-Agents"
                    ${columnBodyRenderer(this._subAgentsRenderer, [])}>
                </vaadin-grid-column>
                <vaadin-grid-column auto-width header="Details"
                    ${columnBodyRenderer(this._detailsRenderer, [])}>
                </vaadin-grid-column>
            </vaadin-grid>
        `;
    }

    _nameRenderer(agent) {
        return html`
            <span>${agent.simpleName}</span>
            ${agent.rootAgent ? html`<span class="root-badge">root</span>` : ''}
        `;
    }

    _typeRenderer(agent) {
        return html`<span class="badge badge-${agent.agentType}">${agent.agentType}</span>`;
    }

    _descriptionRenderer(agent) {
        return html`<span>${agent.description || ''}</span>`;
    }

    _subAgentsRenderer(agent) {
        if (!agent.subAgents || agent.subAgents.length === 0) {
            return html`<span>-</span>`;
        }
        return html`
            <vaadin-vertical-layout>
                ${agent.subAgents.map(sa => {
                    const shortName = sa.substring(sa.lastIndexOf('.') + 1);
                    return html`<code>${shortName}</code>`;
                })}
            </vaadin-vertical-layout>
        `;
    }

    _detailsRenderer(agent) {
        return html`
            <vaadin-details summary="Show">
                <dl class="detail-section">
                    <dt>Full class</dt>
                    <dd><code>${agent.className}</code></dd>

                    ${agent.methods && agent.methods.length > 0 ? html`
                        <dt>Methods</dt>
                        ${agent.methods.map(m => html`<dd><code>${m}</code></dd>`)}
                    ` : ''}

                    ${agent.configAnnotations && agent.configAnnotations.length > 0 ? html`
                        <dt>Configuration</dt>
                        ${agent.configAnnotations.map(a => html`<dd><code>${a}</code></dd>`)}
                    ` : ''}
                </dl>
            </vaadin-details>
        `;
    }
}

customElements.define('qwc-agents', QwcAgents);
