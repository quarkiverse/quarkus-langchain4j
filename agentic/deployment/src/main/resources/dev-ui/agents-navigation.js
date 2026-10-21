/**
 * Key identifying a root agent across pages and application restarts, unlike its index.
 */
export function rootKeyOf(entry) {
    return entry ? (entry.className || entry.typeName) : null;
}

/**
 * Index of the root agent matching {@code key}, or of the first root agent when none matches.
 */
export function rootIndexFor(entries, key) {
    if (entries.length === 0) {
        return 0;
    }
    for (const entry of entries) {
        if (key && rootKeyOf(entry) === key) {
            return entry.index;
        }
    }
    return entries[0].index;
}

/**
 * Replaces the query parameters of the current page with the selected root agent and focused agent, so reloading the
 * page keeps them.
 */
export function syncAgentParams(rootKey, agentId) {
    const url = new URL(window.location.href);
    url.searchParams.delete('root');
    url.searchParams.delete('agent');
    if (rootKey) {
        url.searchParams.set('root', rootKey);
    }
    if (agentId) {
        url.searchParams.set('agent', agentId);
    }
    window.history.replaceState(window.history.state, '', url);
}

/**
 * Navigates to the page of this extension rendered by {@code componentLink}, passing the root agent and the agent to
 * focus as query parameters. Returns false when the page is not registered.
 */
export function goToAgentPage(routerController, componentLink, rootKey, agentId) {
    const pages = routerController.getPagesForCurrentNamespace() || [];
    const page = pages.find(p => p.componentLink === componentLink);
    if (!page) {
        return false;
    }
    const params = new URLSearchParams();
    if (rootKey) {
        params.set('root', rootKey);
    }
    if (agentId) {
        params.set('agent', agentId);
    }
    routerController.goToPath('/' + page.id + '?' + params.toString());
    return true;
}
