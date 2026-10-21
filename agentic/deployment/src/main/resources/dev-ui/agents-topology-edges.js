/**
 * Routes the data-flow edges of the topology page orthogonally around the cards and condition labels, from the output
 * key pill of the producer to the matching input key pill of the consumer.
 */
const EDGE_PAD = 8;
const EDGE_CLEARANCE = 6;
const CONNECTOR_CLEARANCE = 5;
const LANE_GAP = 4;
const LANE_CLUSTER = 8;
const COMB_SPREAD = 12;
const BEND_COST = 24;
const CONGESTION_COST = 12;
const SHARED_FACTOR = 0.3;
const CORNER_RADIUS = 5;
const STEPS = [[1, 0], [-1, 0], [0, 1], [0, -1]];

export function relativeRect(element, origin) {
    const r = element.getBoundingClientRect();
    return { l: r.left - origin.left, t: r.top - origin.top, r: r.right - origin.left, b: r.bottom - origin.top };
}

function sortedUnique(values) {
    const sorted = values.slice().sort((a, b) => a - b);
    const result = [];
    for (const v of sorted) {
        if (result.length === 0 || v !== result[result.length - 1]) {
            result.push(v);
        }
    }
    return result;
}

function lowerBound(values, v) {
    let lo = 0;
    let hi = values.length;
    while (lo < hi) {
        const mid = (lo + hi) >> 1;
        if (values[mid] < v) {
            lo = mid + 1;
        } else {
            hi = mid;
        }
    }
    return lo;
}

class MinHeap {
    constructor() {
        this.keys = [];
        this.values = [];
    }

    get size() {
        return this.keys.length;
    }

    push(key, value) {
        const keys = this.keys;
        const values = this.values;
        let i = keys.length;
        keys.push(key);
        values.push(value);
        while (i > 0) {
            const parent = (i - 1) >> 1;
            if (keys[parent] <= key) {
                break;
            }
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    pop() {
        const keys = this.keys;
        const values = this.values;
        const top = values[0];
        const lastKey = keys.pop();
        const lastValue = values.pop();
        const n = keys.length;
        if (n > 0) {
            let i = 0;
            while (true) {
                let child = 2 * i + 1;
                if (child >= n) {
                    break;
                }
                if (child + 1 < n && keys[child + 1] < keys[child]) {
                    child++;
                }
                if (keys[child] >= lastKey) {
                    break;
                }
                keys[i] = keys[child];
                values[i] = values[child];
                i = child;
            }
            keys[i] = lastKey;
            values[i] = lastValue;
        }
        return top;
    }
}

/**
 * Sparse orthogonal grid whose lines are the padded obstacle borders and the port coordinates, so the link between
 * two neighbouring nodes is either entirely inside an obstacle or entirely free. Routes are found with A*, the cost
 * being the length plus a penalty per bend and per link already taken by another group. Links already taken by the
 * same group (edges leaving the same key pill) are cheaper, so these edges share a trunk. Running along a tree
 * connector is allowed but penalised.
 */
class RoutingGrid {
    constructor(obstacles, connectors, xs, ys) {
        this.xs = sortedUnique(xs);
        this.ys = sortedUnique(ys);
        const size = this.xs.length * this.ys.length;
        this.blocked = new Uint8Array(size);
        this.blockedRight = new Uint8Array(size);
        this.blockedDown = new Uint8Array(size);
        this.usedRight = new Uint16Array(size);
        this.usedDown = new Uint16Array(size);
        this.alongRight = new Uint8Array(size);
        this.alongDown = new Uint8Array(size);
        this.groups = new Map();
        for (const o of obstacles) {
            this._block(o);
        }
        for (const line of connectors) {
            this._discourage(line);
        }
    }

    _discourage(line) {
        const xs = this.xs;
        const ys = this.ys;
        const nx = xs.length;
        const horizontal = line.r - line.l > line.b - line.t;
        const at = horizontal ? (line.t + line.b) / 2 : (line.l + line.r) / 2;
        const lines = horizontal ? ys : xs;
        const across = horizontal ? xs : ys;
        const from = horizontal ? line.l : line.t;
        const to = horizontal ? line.r : line.b;
        for (let k = lowerBound(lines, at - CONNECTOR_CLEARANCE); k < lines.length && lines[k] <= at + CONNECTOR_CLEARANCE; k++) {
            for (let m = 0; m + 1 < across.length; m++) {
                if (across[m + 1] > from && across[m] < to) {
                    if (horizontal) {
                        this.alongRight[k * nx + m] = 1;
                    } else {
                        this.alongDown[m * nx + k] = 1;
                    }
                }
            }
        }
    }

    _block(o) {
        const xs = this.xs;
        const ys = this.ys;
        const nx = xs.length;
        for (let j = lowerBound(ys, o.t); j < ys.length && ys[j] <= o.b; j++) {
            const yInside = ys[j] > o.t && ys[j] < o.b;
            for (let i = lowerBound(xs, o.l); i < nx && xs[i] <= o.r; i++) {
                const xInside = xs[i] > o.l && xs[i] < o.r;
                const id = j * nx + i;
                if (xInside && yInside) {
                    this.blocked[id] = 1;
                }
                if (yInside && i + 1 < nx && xs[i + 1] <= o.r) {
                    this.blockedRight[id] = 1;
                }
                if (xInside && j + 1 < ys.length && ys[j + 1] <= o.b) {
                    this.blockedDown[id] = 1;
                }
            }
        }
    }

    _node(x, y) {
        const i = lowerBound(this.xs, x);
        const j = lowerBound(this.ys, y);
        if (this.xs[i] !== x || this.ys[j] !== y) {
            return -1;
        }
        return j * this.xs.length + i;
    }

    _estimate(node, vertical, goal) {
        const nx = this.xs.length;
        const dx = Math.abs(this.xs[goal % nx] - this.xs[node % nx]);
        const dy = Math.abs(this.ys[(goal / nx) | 0] - this.ys[(node / nx) | 0]);
        if (dx === 0 && dy === 0) {
            return 0;
        }
        return dx + dy + (dx > 0 || !vertical ? BEND_COST : 0);
    }

    /**
     * Leaves the start downwards and reaches the end upwards, as both ports sit below a key pill.
     */
    route(sx, sy, ex, ey, group) {
        if (!this.groups.has(group)) {
            this.groups.set(group, new Uint8Array(this.blocked.length * 2));
        }
        const own = this.groups.get(group);
        const nx = this.xs.length;
        const ny = this.ys.length;
        const start = this._node(sx, sy);
        const goal = this._node(ex, ey);
        if (start < 0 || goal < 0 || this.blocked[start] || this.blocked[goal]) {
            return null;
        }
        const states = this.blocked.length * 2;
        const cost = new Float64Array(states).fill(Infinity);
        const prev = new Int32Array(states).fill(-1);
        const done = new Uint8Array(states);
        const heap = new MinHeap();
        cost[start * 2 + 1] = 0;
        heap.push(this._estimate(start, 1, goal), start * 2 + 1);
        while (heap.size > 0) {
            const state = heap.pop();
            if (done[state]) {
                continue;
            }
            done[state] = 1;
            const node = state >> 1;
            if (node === goal) {
                return this._trace(prev, state, own);
            }
            const vertical = state & 1;
            const i = node % nx;
            const j = (node - i) / nx;
            for (const [di, dj] of STEPS) {
                const ni = i + di;
                const nj = j + dj;
                if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) {
                    continue;
                }
                const next = nj * nx + ni;
                const link = Math.min(node, next);
                const isVertical = dj !== 0 ? 1 : 0;
                if (this.blocked[next] || (isVertical ? this.blockedDown[link] : this.blockedRight[link])) {
                    continue;
                }
                let step = isVertical ? Math.abs(this.ys[nj] - this.ys[j]) : Math.abs(this.xs[ni] - this.xs[i]);
                if (own[link * 2 + isVertical]) {
                    step *= SHARED_FACTOR;
                } else {
                    step += (isVertical ? this.usedDown[link] : this.usedRight[link]) * CONGESTION_COST;
                }
                if (isVertical ? this.alongDown[link] : this.alongRight[link]) {
                    step = step * 2 + BEND_COST;
                }
                if (isVertical !== vertical) {
                    step += BEND_COST;
                }
                if (next === goal && !isVertical) {
                    step += BEND_COST;
                }
                const nextState = next * 2 + isVertical;
                const g = cost[state] + step;
                if (g < cost[nextState]) {
                    cost[nextState] = g;
                    prev[nextState] = state;
                    heap.push(g + this._estimate(next, isVertical, goal), nextState);
                }
            }
        }
        return null;
    }

    _trace(prev, state, own) {
        const nx = this.xs.length;
        const nodes = [];
        for (let s = state; s >= 0; s = prev[s]) {
            nodes.push(s >> 1);
        }
        nodes.reverse();
        const points = [];
        for (let k = 0; k < nodes.length; k++) {
            const node = nodes[k];
            points.push({ x: this.xs[node % nx], y: this.ys[(node / nx) | 0] });
            if (k > 0) {
                const link = Math.min(node, nodes[k - 1]);
                const isVertical = Math.abs(node - nodes[k - 1]) === 1 ? 0 : 1;
                if (!own[link * 2 + isVertical]) {
                    own[link * 2 + isVertical] = 1;
                    if (isVertical) {
                        this.usedDown[link]++;
                    } else {
                        this.usedRight[link]++;
                    }
                }
            }
        }
        return points;
    }
}

function toSegments(edge, routePoints) {
    const raw = [{ x: routePoints[0].x, y: edge.from.y }].concat(routePoints, [{ x: routePoints[routePoints.length - 1].x, y: edge.to.y }]);
    const points = [];
    for (const p of raw) {
        const last = points[points.length - 1];
        if (last && last.x === p.x && last.y === p.y) {
            continue;
        }
        const before = points[points.length - 2];
        if (before && ((before.x === last.x && last.x === p.x) || (before.y === last.y && last.y === p.y))) {
            points[points.length - 1] = p;
        } else {
            points.push(p);
        }
    }
    const segments = [];
    for (let k = 0; k + 1 < points.length; k++) {
        const a = points[k];
        const b = points[k + 1];
        const vertical = a.x === b.x;
        segments.push({
            vertical,
            c: vertical ? a.x : a.y,
            a: vertical ? Math.min(a.y, b.y) : Math.min(a.x, b.x),
            b: vertical ? Math.max(a.y, b.y) : Math.max(a.x, b.x),
            edge,
        });
    }
    return { edge, segments };
}

function toPoints(route) {
    const segments = route.segments;
    const first = segments[0];
    const last = segments[segments.length - 1];
    const points = [{ x: first.c, y: route.edge.from.y }];
    for (let k = 0; k + 1 < segments.length; k++) {
        const s = segments[k];
        const n = segments[k + 1];
        points.push(s.vertical ? { x: s.c, y: n.c } : { x: n.c, y: s.c });
    }
    if (last.c !== route.edge.to.x) {
        points.push({ x: last.c, y: route.edge.to.mergeY }, { x: route.edge.to.x, y: route.edge.to.mergeY });
    }
    points.push({ x: route.edge.to.x, y: route.edge.to.y });
    return points;
}

function overlapping(s1, s2) {
    return s1.a < s2.b - 1 && s2.a < s1.b - 1;
}

function conflicting(s1, s2) {
    return s1.edge.from.id !== s2.edge.from.id && overlapping(s1, s2);
}

/**
 * Fans the edges entering the same key pill out across the pill width, ordered by the side they arrive from. They
 * merge again just below the card so the pill gets a single arrow.
 */
function spreadPorts(routes) {
    const ports = new Map();
    for (const route of routes) {
        const id = route.edge.to.id;
        if (!ports.has(id)) {
            ports.set(id, []);
        }
        const last = route.segments[route.segments.length - 1];
        const arrival = route.segments[route.segments.length - 2];
        const towards = Math.abs(arrival.a - last.c) > Math.abs(arrival.b - last.c) ? arrival.a : arrival.b;
        ports.get(id).push({ segment: last, towards });
    }
    for (const members of ports.values()) {
        if (members.length < 2) {
            continue;
        }
        members.sort((m1, m2) => m1.towards - m2.towards || m1.segment.edge.key.localeCompare(m2.segment.edge.key));
        const n = members.length;
        const port = members[0].segment.edge.to;
        const gap = Math.min(LANE_GAP, COMB_SPREAD / (n - 1), Math.max(0, port.hi - port.lo) / (n - 1));
        const fromLeft = members.filter(m => m.towards < port.x).length;
        const fromRight = members.filter(m => m.towards > port.x).length;
        const ideal = port.x - gap * (fromLeft + (n - fromLeft - fromRight) / 2 - 0.5);
        const first = Math.max(port.lo, Math.min(port.hi - gap * (n - 1), ideal));
        for (let k = 0; k < n; k++) {
            members[k].segment.c = first + k * gap;
        }
    }
}

function freeRange(vertical, c, from, to, obstacles, withConnectors) {
    let lo = -Infinity;
    let hi = Infinity;
    for (const o of obstacles) {
        if (o.connector && !withConnectors) {
            continue;
        }
        const near = vertical ? o.t : o.l;
        const far = vertical ? o.b : o.r;
        if (near >= to || far <= from) {
            continue;
        }
        const before = vertical ? o.r : o.b;
        const after = vertical ? o.l : o.t;
        const clearance = o.connector ? CONNECTOR_CLEARANCE : EDGE_CLEARANCE;
        if (before <= c) {
            lo = Math.max(lo, before + clearance);
        } else if (after >= c) {
            hi = Math.min(hi, after - clearance);
        }
    }
    lo = lo === -Infinity ? c - 40 : lo;
    hi = hi === Infinity ? c + 40 : hi;
    if (hi < lo) {
        return withConnectors ? freeRange(vertical, c, from, to, obstacles, false) : [c, c];
    }
    return [lo, hi];
}

function assignLanes(channel) {
    const lanes = [];
    for (const s of channel) {
        const line = lanes.filter(lane => lane.c === s.c && !lane.segments.some(other => conflicting(other, s)));
        let target = line.find(lane => lane.segments.some(other => other.edge.from.id === s.edge.from.id && overlapping(other, s)));
        if (!target) {
            target = line[0];
        }
        if (!target) {
            target = { c: s.c, segments: [] };
            lanes.push(target);
        }
        target.segments.push(s);
    }
    return lanes;
}

function laneRange(lane, obstacles, vertical) {
    if (lane.segments.some(s => s.pinned)) {
        return [lane.c, lane.c];
    }
    let lo = -Infinity;
    let hi = Infinity;
    for (const s of lane.segments) {
        const [l, h] = freeRange(vertical, s.c, s.a, s.b, obstacles, true);
        lo = Math.max(lo, l);
        hi = Math.min(hi, h);
    }
    return hi < lo ? [lane.c, lane.c] : [lo, hi];
}

/**
 * Places the lanes, in order, at least gap apart from every lane they overlap, staying inside their own free range.
 * The gap shrinks when the channel is too narrow.
 */
function placeLanes(lanes, obstacles, vertical) {
    const n = lanes.length;
    const ranges = lanes.map(lane => laneRange(lane, obstacles, vertical));
    const overlaps = lanes.map(l1 => lanes.map(l2 => l1 !== l2 && l1.segments.some(s => l2.segments.some(t => overlapping(s, t)))));
    const clamp = (v, k) => Math.max(ranges[k][0], Math.min(ranges[k][1], v));
    let positions = null;
    for (const gap of [LANE_GAP, LANE_GAP * 0.75, LANE_GAP / 2]) {
        const pos = lanes.map((lane, k) => {
            const line = lanes.filter(other => other.c === lane.c);
            return clamp(lane.c + (line.indexOf(lane) - (line.length - 1) / 2) * gap, k);
        });
        for (let pass = 0; pass < 2; pass++) {
            for (let k = 1; k < n; k++) {
                for (let j = 0; j < k; j++) {
                    if (overlaps[j][k]) {
                        pos[k] = clamp(Math.max(pos[k], pos[j] + gap), k);
                    }
                }
            }
            for (let k = n - 2; k >= 0; k--) {
                for (let j = k + 1; j < n; j++) {
                    if (overlaps[j][k]) {
                        pos[k] = clamp(Math.min(pos[k], pos[j] - gap), k);
                    }
                }
            }
        }
        positions = pos;
        let feasible = true;
        for (let k = 0; k < n && feasible; k++) {
            for (let j = 0; j < k; j++) {
                if (overlaps[j][k] && positions[k] - positions[j] < gap - 0.01) {
                    feasible = false;
                    break;
                }
            }
        }
        if (feasible) {
            break;
        }
    }
    return positions;
}

/**
 * Edge segments on nearby parallel lines with overlapping extents form a channel. Within a channel each segment joins
 * the lane of its own trunk on the same line, or else the first lane of that line free over its extent, in key order.
 * The segments ending at a pill keep the position given by spreadPorts, the other lanes move away from them.
 */
function nudgeChannels(routes, obstacles, vertical) {
    const segments = [];
    for (const route of routes) {
        const last = route.segments.length - 1;
        route.segments.forEach((s, k) => {
            if (s.vertical === vertical) {
                s.pinned = k === 0 || k === last;
                segments.push(s);
            }
        });
    }
    segments.sort((s1, s2) => s1.c - s2.c);
    const parent = segments.map((s, i) => i);
    const find = i => {
        while (parent[i] !== i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    };
    for (let i = 0; i < segments.length; i++) {
        for (let j = i + 1; j < segments.length && segments[j].c - segments[i].c <= LANE_CLUSTER; j++) {
            if (conflicting(segments[i], segments[j])) {
                parent[find(j)] = find(i);
            }
        }
    }
    const channels = new Map();
    for (let i = 0; i < segments.length; i++) {
        const root = find(i);
        if (!channels.has(root)) {
            channels.set(root, []);
        }
        channels.get(root).push(segments[i]);
    }
    for (const channel of channels.values()) {
        if (channel.length < 2) {
            continue;
        }
        channel.sort((s1, s2) => s1.c - s2.c || s1.edge.key.localeCompare(s2.edge.key) || s1.edge.order - s2.edge.order);
        const lanes = assignLanes(channel);
        if (lanes.length < 2) {
            continue;
        }
        const positions = placeLanes(lanes, obstacles, vertical);
        lanes.forEach((lane, k) => {
            for (const s of lane.segments) {
                if (!s.pinned) {
                    s.c = positions[k];
                }
            }
        });
    }
}

/**
 * Routes every edge orthogonally around the obstacles, then separates the segments sharing a channel. Returns one
 * polyline per edge, or null when no route exists.
 */
export function routeDataFlow(edges, obstacles, connectors) {
    const padded = obstacles.map(o => ({
        l: Math.round(o.l - EDGE_PAD), t: Math.round(o.t - EDGE_PAD),
        r: Math.round(o.r + EDGE_PAD), b: Math.round(o.b + EDGE_PAD),
    }));
    const xs = [];
    const ys = [];
    for (const o of padded) {
        xs.push(o.l, o.r);
        ys.push(o.t, o.b);
    }
    for (const edge of edges) {
        xs.push(edge.from.x, edge.to.x);
        ys.push(edge.from.exitY, edge.to.exitY);
    }
    const grid = new RoutingGrid(padded, connectors, xs, ys);
    const routes = edges.map(edge => {
        const points = grid.route(edge.from.x, edge.from.exitY, edge.to.x, edge.to.exitY, edge.from.id);
        return points ? toSegments(edge, points) : null;
    });
    const routed = routes.filter(r => r !== null);
    spreadPorts(routed);
    const walls = obstacles.concat(connectors);
    for (let pass = 0; pass < 2; pass++) {
        nudgeChannels(routed, walls, false);
        nudgeChannels(routed, walls, true);
    }
    return routes.map(r => r ? toPoints(r) : null);
}

export function roundedPath(points) {
    const fmt = p => Math.round(p.x * 10) / 10 + ',' + Math.round(p.y * 10) / 10;
    const towards = (p, q, r) => {
        const d = Math.hypot(q.x - p.x, q.y - p.y) || 1;
        return { x: p.x + (q.x - p.x) * r / d, y: p.y + (q.y - p.y) * r / d };
    };
    let d = 'M' + fmt(points[0]);
    for (let k = 1; k + 1 < points.length; k++) {
        const a = points[k - 1];
        const p = points[k];
        const b = points[k + 1];
        const r = Math.min(CORNER_RADIUS, Math.hypot(p.x - a.x, p.y - a.y) / 2, Math.hypot(b.x - p.x, b.y - p.y) / 2);
        d += ' L' + fmt(towards(p, a, r)) + ' Q' + fmt(p) + ' ' + fmt(towards(p, b, r));
    }
    return d + ' L' + fmt(points[points.length - 1]);
}

export function curvePath(x1, y1, x2, y2) {
    const dx = x2 - x1;
    const dy = y2 - y1;
    const dist = Math.sqrt(dx * dx + dy * dy);
    const bend = Math.min(60, dist * 0.3);
    const cpx = (x1 + x2) / 2 + (-dy / (dist || 1) * bend);
    const cpy = (y1 + y2) / 2 + (dx / (dist || 1) * bend);
    return 'M' + x1 + ',' + y1 + ' Q' + cpx + ',' + cpy + ' ' + x2 + ',' + y2;
}

/**
 * The port of a pill on a wrapped row runs through the pills below it, so it is moved sideways to cross them outside
 * their text when possible.
 */
function pillPortX(pill, rect, origin) {
    const below = [];
    for (const other of pill.parentElement.children) {
        const o = relativeRect(other, origin);
        if (other !== pill && o.t >= rect.b - 1 && o.l < rect.r && o.r > rect.l) {
            below.push(o);
        }
    }
    const center = (rect.l + rect.r) / 2;
    let best = Math.round(center);
    let bestCost = Infinity;
    for (let x = Math.ceil(rect.l + 3); x <= rect.r - 3; x++) {
        let cost = Math.abs(x - center) / 10;
        for (const o of below) {
            if (x > o.l + 4 && x < o.r - 4) {
                cost += 100;
            } else if (x >= o.l - 2 && x <= o.r + 2) {
                cost += 10;
            }
        }
        if (cost < bestCost) {
            bestCost = cost;
            best = x;
        }
    }
    return best;
}

export function portOf(card, pillClass, key, origin) {
    const cardRect = relativeRect(card, origin);
    const exitY = Math.round(cardRect.b + EDGE_PAD);
    for (const pill of card.querySelectorAll('.' + pillClass)) {
        if (pill.dataset.key === key) {
            const r = relativeRect(pill, origin);
            return {
                id: card.dataset.dfIndex + pillClass + key, x: pillPortX(pill, r, origin), y: r.b, exitY,
                mergeY: cardRect.b + 3, lo: r.l + 3, hi: r.r - 3,
            };
        }
    }
    const x = Math.round((cardRect.l + cardRect.r) / 2);
    return { id: card.dataset.dfIndex + pillClass, x, y: cardRect.b, exitY, mergeY: cardRect.b + 3, lo: x - 6, hi: x + 6 };
}

function connectorLine(x1, y1, x2, y2) {
    return { l: Math.min(x1, x2) - 1, t: Math.min(y1, y2) - 1, r: Math.max(x1, x2) + 1, b: Math.max(y1, y2) + 1, connector: true };
}

/**
 * The tree connectors are CSS pseudo-elements, so their lines are rebuilt from the nested lists.
 */
export function treeConnectors(tree, origin) {
    const lines = [];
    for (const ul of tree.querySelectorAll('li > ul')) {
        const items = [...ul.children];
        if (items.length === 0) {
            continue;
        }
        const list = relativeRect(ul, origin);
        const rects = items.map(li => relativeRect(li, origin));
        const bus = rects[0].t;
        const centers = rects.map(r => (r.l + r.r) / 2);
        lines.push(connectorLine((list.l + list.r) / 2, list.t, (list.l + list.r) / 2, bus));
        if (items.length > 1) {
            lines.push(connectorLine(centers[0], bus, centers[centers.length - 1], bus));
        }
        items.forEach((li, i) => {
            const card = li.querySelector(':scope > .node-card');
            if (card) {
                lines.push(connectorLine(centers[i], bus, centers[i], relativeRect(card, origin).t));
            }
        });
    }
    return lines;
}
