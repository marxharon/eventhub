/**
 * EventHub - Cliente HTTP Unificado via API Gateway (:8080)
 * Gerencia autenticação JWT (RN07), Correlation-ID (RQ06) e tratamento padronizado de erros.
 */
window.EventHubAPI = {
    getToken() {
        return sessionStorage.getItem('eventhub_jwt') || null;
    },

    setAuth(token, user) {
        if (token) {
            sessionStorage.setItem('eventhub_jwt', token);
            sessionStorage.setItem('eventhub_user', JSON.stringify(user));
        } else {
            sessionStorage.removeItem('eventhub_jwt');
            sessionStorage.removeItem('eventhub_user');
        }
    },

    getStoredUser() {
        try {
            const raw = sessionStorage.getItem('eventhub_user');
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    },

    async request(path, options = {}) {
        const headers = Object.assign({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        }, options.headers || {});

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const startTime = performance.now();
        try {
            const response = await fetch(path, {
                ...options,
                headers
            });
            const latencyMs = Math.round(performance.now() - startTime);
            const correlationId = response.headers.get('X-Correlation-ID') || 'local';

            let data = null;
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                data = await response.json();
            } else {
                const text = await response.text();
                try { data = JSON.parse(text); } catch (_) { data = { raw: text }; }
            }

            return {
                ok: response.ok,
                status: response.status,
                latencyMs,
                correlationId,
                data
            };
        } catch (error) {
            const latencyMs = Math.round(performance.now() - startTime);
            return {
                ok: false,
                status: 0,
                latencyMs,
                correlationId: 'network-error',
                data: { message: error.message || 'Falha de conexão com o API Gateway' }
            };
        }
    },

    async checkServiceHealth(endpoint) {
        const res = await this.request(endpoint, { method: 'GET' });
        return {
            online: res.ok && res.status === 200,
            status: res.status,
            latencyMs: res.latencyMs,
            details: res.data
        };
    }
};
