/**
 * EventHub - Controlador Principal da Aplicação Web SPA (Vue.js 3)
 * Arquitetura de Navegação:
 * - Inicia obrigatoriamente na Tela de Login (ou Tela de Cadastro separada).
 * - Libera o Menu Lateral somente após autenticação válida no user-service.
 * - Exibe apenas os menus compatíveis com o perfil (PARTICIPANT ou ORGANIZER).
 * - Mantém listagens, formulários de criação (Novo Evento, Nova Atividade) e Validadores em telas separadas.
 */
const { createApp, reactive, computed, onMounted } = Vue;

createApp({
    setup() {
        const state = reactive(window.EventHubStore.createInitialState());
        const isCheckingHealth = Vue.ref(false);
        const lastHealthCheckTime = Vue.ref('—');
        const isSubmitting = Vue.ref(false);

        // Definição limpa de menus por perfil (sem códigos de requisitos poluindo a tela)
        const navigationGroups = [
            {
                title: 'Área do Participante',
                roles: ['PARTICIPANT'],
                items: [
                    { id: 'explore-events', label: 'Explorar Eventos', icon: 'ri-compass-3-line' },
                    { id: 'my-registrations', label: 'Minhas Inscrições', icon: 'ri-ticket-2-line' },
                    { id: 'payments-checkout', label: 'Meus Pagamentos', icon: 'ri-bank-card-line' },
                    { id: 'my-certificates', label: 'Meus Certificados', icon: 'ri-award-line' }
                ]
            },
            {
                title: 'Gestão de Eventos',
                roles: ['ORGANIZER'],
                items: [
                    { id: 'events-list', label: 'Meus Eventos', icon: 'ri-calendar-event-line' },
                    { id: 'event-form', label: 'Novo Evento', icon: 'ri-calendar-todo-line' },
                    { id: 'checkin-terminal', label: 'Credenciamento & Check-in', icon: 'ri-qr-scan-2-line' },
                    { id: 'organizer-analytics', label: 'Relatórios & Indicadores', icon: 'ri-bar-chart-grouped-line' }
                ]
            },
            {
                title: 'Validação & Infraestrutura',
                roles: ['ORGANIZER', 'PARTICIPANT'],
                items: [
                    { id: 'system-validators', label: 'Validadores do Sistema', icon: 'ri-shield-check-line' },
                    { id: 'architecture-status', label: 'Status dos Serviços', icon: 'ri-server-line' }
                ]
            },
            {
                title: 'Conta',
                roles: ['ORGANIZER', 'PARTICIPANT'],
                items: [
                    { id: 'user-profile', label: 'Meu Perfil', icon: 'ri-user-settings-line' }
                ]
            }
        ];

        const visibleNavigationGroups = computed(() => {
            if (!state.isAuthenticated || !state.currentUser) return [];
            const userRole = state.currentUser.role;
            return navigationGroups.filter(g => g.roles.includes(userRole));
        });

        const activeMenuItem = computed(() => {
            for (const group of navigationGroups) {
                const found = group.items.find(i => i.id === state.activeView);
                if (found) return found;
            }
            if (state.activeView === 'event-program') {
                return { id: 'event-program', label: 'Programação do Evento', icon: 'ri-list-check-2' };
            }
            if (state.activeView === 'activity-form') {
                return { id: 'activity-form', label: 'Nova Atividade do Evento', icon: 'ri-play-list-add-line' };
            }
            if (state.activeView === 'participant-event-details') {
                return { id: 'participant-event-details', label: 'Programação e Vagas do Evento', icon: 'ri-calendar-check-line' };
            }
            return { id: 'events-list', label: 'Painel EventHub', icon: 'ri-dashboard-line' };
        });

        const onlineServicesCount = computed(() => {
            return state.microservices.filter(s => s.online).length;
        });

        // Formulários de Autenticação e Perfil
        const loginForm = reactive({
            email: '',
            password: ''
        });

        const registerForm = reactive({
            name: '',
            email: '',
            password: '',
            role: 'PARTICIPANT',
            organizationName: ''
        });

        const profileForm = reactive({
            name: state.currentUser?.name || '',
            organizationName: state.currentUser?.organizationName || ''
        });

        // Estado de Eventos e Atividades (Telas separadas)
        const organizerEvents = Vue.ref([]);
        const isLoadingEvents = Vue.ref(false);
        const selectedEvent = Vue.ref(null);
        const editingEventId = Vue.ref(null);

        const eventForm = reactive({
            title: '',
            description: '',
            organization: '',
            startDate: '2026-11-25T09:00:00',
            endDate: '2026-11-27T18:00:00'
        });

        const activityForm = reactive({
            eventId: null,
            title: '',
            description: '',
            speaker: '',
            location: '',
            startTime: '2026-11-25T10:00:00',
            endTime: '2026-11-25T12:00:00',
            capacity: 15,
            price: 0,
            waitlistEnabled: true
        });

        // Estado da Tela de Validadores
        const demoAccounts = Vue.ref([]);
        const accessValidatorResult = Vue.ref(null);
        const notificationFailureMode = Vue.ref(false);
        const notificationTestResult = Vue.ref(null);

        async function loadUserNotifications() {
            if (!state.currentUser || !state.currentUser.id) return;
            const res = await window.EventHubAPI.request(`/api/notifications/user/${state.currentUser.id}`);
            if (res.ok && Array.isArray(res.data)) {
                state.notifications = res.data;
            }
        }

        async function openNotificationsDrawer() {
            state.notificationDrawerOpen = true;
            await loadUserNotifications();
        }

        async function loadNotificationFailureState() {
            const res = await window.EventHubAPI.request('/api/notifications/simulate-failure');
            if (res.ok && res.data && typeof res.data.simulateFailure === 'boolean') {
                notificationFailureMode.value = res.data.simulateFailure;
            }
        }

        async function toggleNotificationFailureMode(failState) {
            const res = await window.EventHubAPI.request(`/api/notifications/simulate-failure?fail=${failState}`, {
                method: 'POST'
            });
            if (res.ok && res.data) {
                notificationFailureMode.value = res.data.simulateFailure;
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: res.data.simulateFailure ? 'warning' : 'success',
                        title: res.data.message,
                        showConfirmButton: false,
                        timer: 2500
                    });
                }
            }
        }

        async function sendTestNotification() {
            if (!state.currentUser) return;
            const res = await window.EventHubAPI.request('/api/notifications/send', {
                method: 'POST',
                body: JSON.stringify({
                    userId: state.currentUser.id,
                    recipientEmail: state.currentUser.email,
                    eventType: 'SYSTEM_TEST_NOTIFICATION',
                    message: `Comunicado de teste enviado para ${state.currentUser.name} às ${new Date().toLocaleTimeString('pt-BR')}.`
                })
            });
            notificationTestResult.value = {
                httpStatus: res.status,
                ok: res.ok,
                latencyMs: res.latencyMs,
                payload: res.data
            };
            if (res.ok) {
                await loadUserNotifications();
            }
        }

        // Estado do Passo 5: Busca e Catálogo Público para Participantes (search-service :8083)
        const publishedEventsCatalog = Vue.ref([]);
        const isSearchingCatalog = Vue.ref(false);
        const selectedCatalogEvent = Vue.ref(null);
        const searchFilters = reactive({
            keyword: '',
            priceFilter: 'ALL', // 'ALL' | 'FREE' | 'PAID'
            onlyAvailable: false
        });

        async function searchPublishedCatalog() {
            isSearchingCatalog.value = true;
            try {
                const params = new URLSearchParams();
                if (searchFilters.keyword && searchFilters.keyword.trim()) {
                    params.set('keyword', searchFilters.keyword.trim());
                }
                if (searchFilters.priceFilter === 'FREE') {
                    params.set('onlyFree', 'true');
                } else if (searchFilters.priceFilter === 'PAID') {
                    params.set('onlyFree', 'false');
                }
                if (searchFilters.onlyAvailable) {
                    params.set('onlyAvailable', 'true');
                }

                const queryString = params.toString() ? `?${params.toString()}` : '';
                const res = await window.EventHubAPI.request(`/api/search/events${queryString}`);
                if (res.ok && Array.isArray(res.data)) {
                    publishedEventsCatalog.value = res.data;
                    if (selectedCatalogEvent.value) {
                        const updated = res.data.find(e => e.id === selectedCatalogEvent.value.id);
                        if (updated) selectedCatalogEvent.value = updated;
                    }
                }
            } finally {
                isSearchingCatalog.value = false;
            }
        }

        function clearSearchFilters() {
            searchFilters.keyword = '';
            searchFilters.priceFilter = 'ALL';
            searchFilters.onlyAvailable = false;
            searchPublishedCatalog();
        }

        async function openParticipantEventDetails(ev) {
            const params = new URLSearchParams();
            if (searchFilters.priceFilter === 'FREE') {
                params.set('onlyFree', 'true');
            } else if (searchFilters.priceFilter === 'PAID') {
                params.set('onlyFree', 'false');
            }
            if (searchFilters.onlyAvailable) {
                params.set('onlyAvailable', 'true');
            }
            const qs = params.toString() ? `?${params.toString()}` : '';
            const res = await window.EventHubAPI.request(`/api/search/events/${ev.id}${qs}`);
            selectedCatalogEvent.value = (res.ok && res.data) ? res.data : ev;
            state.activeView = 'participant-event-details';
        }

        function applyTheme(theme) {
            state.theme = theme;
            document.documentElement.setAttribute('data-theme', theme);
            localStorage.setItem('eventhub_theme', theme);
        }

        function toggleTheme() {
            applyTheme(state.theme === 'light' ? 'dark' : 'light');
        }

        function syncProfileForm() {
            if (state.currentUser) {
                profileForm.name = state.currentUser.name || '';
                profileForm.organizationName = state.currentUser.organizationName || '';
            }
        }

        function selectView(viewId) {
            if (viewId === 'event-form' && editingEventId.value === null) {
                resetEventForm();
            }
            if (viewId === 'explore-events') {
                searchPublishedCatalog();
            }
            state.activeView = viewId;
        }

        function openAuthScreen(screen) {
            state.authScreen = screen;
        }

        // =====================================================================
        // AUTENTICAÇÃO (LOGIN, CADASTRO E LOGOUT)
        // =====================================================================
        async function submitLogin() {
            isSubmitting.value = true;
            try {
                const res = await window.EventHubAPI.request('/api/users/login', {
                    method: 'POST',
                    body: JSON.stringify({
                        email: loginForm.email.trim(),
                        password: loginForm.password
                    })
                });

                if (res.ok && res.data && res.data.token) {
                    window.EventHubAPI.setAuth(res.data.token, res.data.user);
                    state.currentUser = res.data.user;
                    state.isAuthenticated = true;
                    syncProfileForm();
                    accessValidatorResult.value = null;

                    state.activeView = state.currentUser.role === 'ORGANIZER' ? 'events-list' : 'explore-events';
                    loginForm.password = '';

                    await loadInitialAuthenticatedData();

                    if (window.Swal) {
                        Swal.fire({
                            toast: true,
                            position: 'top-end',
                            icon: 'success',
                            title: `Bem-vindo(a), ${state.currentUser.name}!`,
                            showConfirmButton: false,
                            timer: 2000
                        });
                    }
                } else if (window.Swal) {
                    Swal.fire({
                        icon: 'error',
                        title: 'Credenciais Inválidas',
                        text: (res.data && res.data.message) || 'Verifique seu e-mail e senha.'
                    });
                }
            } finally {
                isSubmitting.value = false;
            }
        }

        async function submitRegister() {
            isSubmitting.value = true;
            try {
                const res = await window.EventHubAPI.request('/api/users/register', {
                    method: 'POST',
                    body: JSON.stringify(registerForm)
                });

                if (res.ok && res.data && res.data.token) {
                    window.EventHubAPI.setAuth(res.data.token, res.data.user);
                    state.currentUser = res.data.user;
                    state.isAuthenticated = true;
                    syncProfileForm();

                    registerForm.name = '';
                    registerForm.email = '';
                    registerForm.password = '';
                    registerForm.organizationName = '';

                    state.activeView = state.currentUser.role === 'ORGANIZER' ? 'events-list' : 'explore-events';
                    await loadInitialAuthenticatedData();

                    if (window.Swal) {
                        Swal.fire({
                            icon: 'success',
                            title: 'Cadastro Realizado!',
                            text: `Sua conta foi criada e você já está conectado como ${state.currentUser.name}.`
                        });
                    }
                } else if (window.Swal) {
                    Swal.fire({
                        icon: 'warning',
                        title: 'Não foi possível criar a conta',
                        text: (res.data && res.data.message) || 'Verifique os dados informados.'
                    });
                }
            } finally {
                isSubmitting.value = false;
            }
        }

        function logout() {
            window.EventHubAPI.setAuth(null, null);
            state.currentUser = null;
            state.isAuthenticated = false;
            state.authScreen = 'login';
            state.activeView = 'login';
            loginForm.email = '';
            loginForm.password = '';
            accessValidatorResult.value = null;
        }

        async function submitUpdateProfile() {
            const res = await window.EventHubAPI.request('/api/users/me', {
                method: 'PUT',
                body: JSON.stringify(profileForm)
            });
            if (res.ok && res.data) {
                state.currentUser = res.data;
                window.EventHubAPI.setAuth(window.EventHubAPI.getToken(), res.data);
                await loadDemoAccounts();
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: 'Dados do perfil atualizados com sucesso!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
            } else if (window.Swal) {
                Swal.fire({
                    icon: 'error',
                    title: 'Erro ao atualizar perfil',
                    text: (res.data && res.data.message) || 'Tente novamente.'
                });
            }
        }

        // =====================================================================
        // GESTÃO DE EVENTOS E PROGRAMAÇÃO (EM TELAS SEPARADAS)
        // =====================================================================
        async function loadOrganizerEvents() {
            isLoadingEvents.value = true;
            try {
                const res = await window.EventHubAPI.request('/api/events');
                if (res.ok && Array.isArray(res.data)) {
                    organizerEvents.value = res.data;
                    if (!activityForm.eventId && res.data.length > 0) {
                        activityForm.eventId = res.data[0].id;
                    }
                    if (selectedEvent.value) {
                        const updated = res.data.find(e => e.id === selectedEvent.value.id);
                        if (updated) selectedEvent.value = updated;
                    }
                }
            } finally {
                isLoadingEvents.value = false;
            }
        }

        function resetEventForm() {
            editingEventId.value = null;
            eventForm.title = '';
            eventForm.description = '';
            eventForm.organization = state.currentUser?.organizationName || '';
            eventForm.startDate = '2026-11-25T09:00:00';
            eventForm.endDate = '2026-11-27T18:00:00';
        }

        function openNewEventScreen() {
            resetEventForm();
            state.activeView = 'event-form';
        }

        function openEditEventScreen(ev) {
            editingEventId.value = ev.id;
            eventForm.title = ev.title;
            eventForm.description = ev.description || '';
            eventForm.organization = ev.organization;
            eventForm.startDate = (ev.startDate || '').slice(0, 19);
            eventForm.endDate = (ev.endDate || '').slice(0, 19);
            state.activeView = 'event-form';
        }

        function openEventProgramScreen(ev) {
            selectedEvent.value = ev;
            state.activeView = 'event-program';
        }

        function openNewActivityForEvent(ev) {
            selectedEvent.value = ev;
            activityForm.eventId = ev.id;
            activityForm.title = '';
            activityForm.description = '';
            activityForm.speaker = '';
            activityForm.location = '';
            activityForm.startTime = (ev.startDate || '2026-11-25T10:00:00').slice(0, 19);
            activityForm.endTime = (ev.startDate || '2026-11-25T12:00:00').slice(0, 19);
            state.activeView = 'activity-form';
        }

        async function submitEventForm() {
            const isEditing = !!editingEventId.value;
            const url = isEditing ? `/api/events/${editingEventId.value}` : '/api/events';
            const method = isEditing ? 'PUT' : 'POST';

            const res = await window.EventHubAPI.request(url, {
                method,
                body: JSON.stringify(eventForm)
            });

            if (res.ok) {
                resetEventForm();
                await loadOrganizerEvents();
                state.activeView = 'events-list';
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: isEditing ? 'Evento atualizado com sucesso!' : 'Novo evento cadastrado!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
            } else if (window.Swal) {
                Swal.fire({
                    icon: 'error',
                    title: 'Não foi possível salvar o evento',
                    text: (res.data && res.data.message) || 'Verifique suas permissões e os campos preenchidos.'
                });
            }
        }

        async function changeEventStatus(eventId, action) {
            const res = await window.EventHubAPI.request(`/api/events/${eventId}/${action}`, {
                method: 'PATCH'
            });
            if (res.ok) {
                await loadOrganizerEvents();
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: action === 'publish' ? 'Evento publicado!' : 'Evento cancelado!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
            } else if (window.Swal) {
                Swal.fire({
                    icon: 'error',
                    title: 'Operação não permitida',
                    text: (res.data && res.data.message) || 'Falha ao alterar o status do evento.'
                });
            }
        }

        async function submitActivityForm() {
            if (!activityForm.eventId) return;
            const targetEventId = activityForm.eventId;

            const res = await window.EventHubAPI.request(`/api/events/${targetEventId}/activities`, {
                method: 'POST',
                body: JSON.stringify({
                    title: activityForm.title,
                    description: activityForm.description,
                    speaker: activityForm.speaker,
                    location: activityForm.location,
                    startTime: activityForm.startTime,
                    endTime: activityForm.endTime,
                    capacity: activityForm.capacity,
                    price: activityForm.price,
                    waitlistEnabled: activityForm.waitlistEnabled
                })
            });

            if (res.ok) {
                activityForm.title = '';
                activityForm.description = '';
                activityForm.speaker = '';
                activityForm.location = '';
                await loadOrganizerEvents();
                const parentEvent = organizerEvents.value.find(e => e.id === targetEventId);
                if (parentEvent) {
                    selectedEvent.value = parentEvent;
                    state.activeView = 'event-program';
                } else {
                    state.activeView = 'events-list';
                }
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: 'Atividade adicionada à programação!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
            } else if (window.Swal) {
                Swal.fire({
                    icon: 'error',
                    title: 'Erro ao cadastrar atividade',
                    text: (res.data && res.data.message) || 'Verifique os dados informados.'
                });
            }
        }

        async function markActivityCompleted(activityId) {
            const res = await window.EventHubAPI.request(`/api/events/activities/${activityId}/complete`, {
                method: 'PATCH'
            });
            if (res.ok) {
                await loadOrganizerEvents();
                if (window.Swal) {
                    Swal.fire({
                        toast: true,
                        position: 'top-end',
                        icon: 'success',
                        title: 'Atividade marcada como concluída!',
                        showConfirmButton: false,
                        timer: 2000
                    });
                }
            } else if (window.Swal) {
                Swal.fire({
                    icon: 'error',
                    title: 'Erro ao concluir atividade',
                    text: (res.data && res.data.message) || 'Falha na operação.'
                });
            }
        }

        // =====================================================================
        // VALIDADORES E SAÚDE DOS SERVIÇOS
        // =====================================================================
        async function loadDemoAccounts() {
            const res = await window.EventHubAPI.request('/api/users/demo-accounts');
            if (res.ok && Array.isArray(res.data)) {
                demoAccounts.value = res.data;
            }
        }

        async function testAccessControlValidator(targetUserId) {
            const res = await window.EventHubAPI.request(`/api/users/${targetUserId}`);
            accessValidatorResult.value = {
                targetUserId,
                httpStatus: res.status,
                ok: res.ok,
                latencyMs: res.latencyMs,
                payload: res.data
            };
        }

        async function checkAllHealth() {
            isCheckingHealth.value = true;
            try {
                const gwRes = await window.EventHubAPI.checkServiceHealth('/health');
                state.gatewayHealth.online = gwRes.online;
                state.gatewayHealth.latencyMs = gwRes.latencyMs;

                await Promise.all(state.microservices.map(async (svc) => {
                    const res = await window.EventHubAPI.checkServiceHealth(svc.healthUrl);
                    svc.online = res.online;
                    svc.status = res.status;
                    svc.latencyMs = res.latencyMs;
                }));

                const now = new Date();
                lastHealthCheckTime.value = now.toLocaleTimeString('pt-BR');
            } finally {
                isCheckingHealth.value = false;
            }
        }

        async function loadInitialAuthenticatedData() {
            await checkAllHealth();
            await loadDemoAccounts();
            await loadOrganizerEvents();
            await searchPublishedCatalog();
            await loadUserNotifications();
            await loadNotificationFailureState();
        }

        onMounted(async () => {
            applyTheme(state.theme);
            await checkAllHealth();

            // Se já houver sessão armazenada no navegador, valida se o token continua válido
            if (state.isAuthenticated) {
                const meRes = await window.EventHubAPI.request('/api/users/me');
                if (meRes.ok && meRes.data) {
                    state.currentUser = meRes.data;
                    syncProfileForm();
                    await loadInitialAuthenticatedData();
                } else {
                    logout();
                }
            }
        });

        return {
            state,
            visibleNavigationGroups,
            activeMenuItem,
            onlineServicesCount,
            isCheckingHealth,
            lastHealthCheckTime,
            isSubmitting,
            loginForm,
            registerForm,
            profileForm,
            organizerEvents,
            isLoadingEvents,
            selectedEvent,
            editingEventId,
            eventForm,
            activityForm,
            demoAccounts,
            accessValidatorResult,
            notificationFailureMode,
            notificationTestResult,
            publishedEventsCatalog,
            isSearchingCatalog,
            selectedCatalogEvent,
            searchFilters,
            toggleTheme,
            selectView,
            openAuthScreen,
            submitLogin,
            submitRegister,
            logout,
            submitUpdateProfile,
            loadOrganizerEvents,
            openNewEventScreen,
            openEditEventScreen,
            openEventProgramScreen,
            openNewActivityForEvent,
            submitEventForm,
            changeEventStatus,
            submitActivityForm,
            markActivityCompleted,
            testAccessControlValidator,
            loadUserNotifications,
            openNotificationsDrawer,
            toggleNotificationFailureMode,
            sendTestNotification,
            searchPublishedCatalog,
            clearSearchFilters,
            openParticipantEventDetails,
            checkAllHealth
        };
    }
}).mount('#app');
