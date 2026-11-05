/**
 * EventHub - Store Reativo Global
 * Inicia desautenticado na página de Login até que o usuário forneça credenciais válidas.
 */
window.EventHubStore = {
    createInitialState() {
        const storedUser = window.EventHubAPI.getStoredUser();
        const storedToken = window.EventHubAPI.getToken();
        const isAuthenticated = !!(storedUser && storedToken);

        return {
            isAuthenticated,
            authScreen: 'login', // 'login' | 'register'
            activeView: isAuthenticated
                ? (storedUser.role === 'ORGANIZER' ? 'events-list' : 'explore-events')
                : 'login',
            sidebarCollapsed: false,
            notificationDrawerOpen: false,
            theme: localStorage.getItem('eventhub_theme') || 'light',
            currentUser: isAuthenticated ? storedUser : null,
            gatewayHealth: {
                online: false,
                latencyMs: 0,
                port: 8080
            },
            microservices: [
                {
                    id: 'user-service',
                    number: 1,
                    name: 'Serviço de Usuários e Perfis',
                    stack: 'Java 21 / Spring Boot 3',
                    port: 8081,
                    healthUrl: '/api/users/health',
                    swaggerUrl: '/swagger.html?urls.primaryName=1.%20user-service%20(Java%2021%20%2F%20Spring%20Boot%20%3A8081)',
                    openapiUrl: '/api/users/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                },
                {
                    id: 'event-service',
                    number: 2,
                    name: 'Serviço de Eventos e Programação',
                    stack: 'Java 21 / Spring Boot 3',
                    port: 8082,
                    healthUrl: '/api/events/health',
                    swaggerUrl: '/swagger.html?urls.primaryName=2.%20event-service%20(Java%2021%20%2F%20Spring%20Boot%20%3A8082)',
                    openapiUrl: '/api/events/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                },
                {
                    id: 'search-service',
                    number: 3,
                    name: 'Serviço de Busca e Catálogo',
                    stack: 'Python 3.12 / FastAPI',
                    port: 8083,
                    healthUrl: '/api/search/health',
                    swaggerUrl: '/api/search/docs',
                    openapiUrl: '/api/search/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                },
                {
                    id: 'registration-service',
                    number: 4,
                    name: 'Serviço de Inscrições e Relatórios',
                    stack: 'Java 21 / Spring Boot 3',
                    port: 8084,
                    healthUrl: '/api/registrations/health',
                    swaggerUrl: '/swagger.html?urls.primaryName=4.%20registration-service%20(Java%2021%20%2F%20Spring%20Boot%20%3A8084)',
                    openapiUrl: '/api/registrations/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                },
                {
                    id: 'payment-service',
                    number: 5,
                    name: 'Serviço de Pagamentos',
                    stack: 'Python 3.12 / FastAPI',
                    port: 8085,
                    healthUrl: '/api/payments/health',
                    swaggerUrl: '/api/payments/docs',
                    openapiUrl: '/api/payments/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                },
                {
                    id: 'notification-service',
                    number: 6,
                    name: 'Serviço de Notificações',
                    stack: 'Python 3.12 / FastAPI',
                    port: 8086,
                    healthUrl: '/api/notifications/health',
                    swaggerUrl: '/api/notifications/docs',
                    openapiUrl: '/api/notifications/openapi.json',
                    online: false,
                    status: 0,
                    latencyMs: 0
                }
            ],
            notifications: []
        };
    }
};
