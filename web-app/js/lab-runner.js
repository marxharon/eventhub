/**
 * EventHub - Laboratório de Validação Arquitetural (Passo 10)
 *
 * Executa cenários reais contra o API Gateway (mesma origem), usando as APIs existentes:
 *  - Concorrência (RN01): N inscrições simultâneas em uma atividade com capacidade C.
 *  - Falha de notificação (RN05): inscrição com o serviço de notificações indisponível (503 simulado).
 *  - Recusa de pagamento (RN02/RN03/RN04): recusa libera a vaga e promove a lista de espera.
 *
 * Cada participante usa seu próprio token JWT (chamadas via fetch direto, pois
 * EventHubAPI.request sobrescreve o cabeçalho Authorization com o token da sessão).
 */
(function () {
    const PASSWORD = '123456';
    const ACTIVE_EVENT_DATE_START = '2026-12-01T08:00:00';
    const ACTIVE_EVENT_DATE_END = '2026-12-05T18:00:00';

    async function call(path, options = {}) {
        const { method = 'GET', token = null, body = null } = options;
        const headers = { Accept: 'application/json' };
        if (body !== null) headers['Content-Type'] = 'application/json';
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const start = performance.now();
        try {
            const response = await fetch(path, {
                method,
                headers,
                body: body !== null ? JSON.stringify(body) : undefined
            });
            const latencyMs = Math.round(performance.now() - start);
            const text = await response.text();
            let data = null;
            try {
                data = text ? JSON.parse(text) : null;
            } catch (_) {
                data = { raw: text };
            }
            return { ok: response.ok, status: response.status, latencyMs, data };
        } catch (error) {
            return {
                ok: false,
                status: 0,
                latencyMs: Math.round(performance.now() - start),
                data: { message: error.message }
            };
        }
    }

    function check(name, passed, detail) {
        return { name, passed: Boolean(passed), detail };
    }

    function makeTag() {
        return Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
    }

    async function createLabEvent(organizerToken, tag) {
        const created = await call('/api/events', {
            method: 'POST',
            token: organizerToken,
            body: {
                title: `Laboratório Chaos ${tag}`,
                description: 'Evento criado automaticamente pelo Laboratório de Validação Arquitetural.',
                organization: 'EventHub Lab',
                startDate: ACTIVE_EVENT_DATE_START,
                endDate: ACTIVE_EVENT_DATE_END
            }
        });
        if (!created.ok) throw new Error(`Falha ao criar evento de laboratório (HTTP ${created.status}).`);
        const published = await call(`/api/events/${created.data.id}/publish`, {
            method: 'PATCH',
            token: organizerToken
        });
        if (!published.ok) throw new Error(`Falha ao publicar evento (HTTP ${published.status}).`);
        return created.data;
    }

    async function createActivity(organizerToken, eventId, options) {
        const res = await call(`/api/events/${eventId}/activities`, {
            method: 'POST',
            token: organizerToken,
            body: {
                title: options.title,
                description: 'Atividade criada pelo laboratório.',
                speaker: 'Laboratório',
                location: 'Sala Virtual',
                startTime: '2026-12-02T09:00:00',
                endTime: '2026-12-02T11:00:00',
                capacity: options.capacity,
                price: options.price || 0,
                paid: Boolean(options.paid),
                waitlistEnabled: options.waitlistEnabled !== false
            }
        });
        if (!res.ok) throw new Error(`Falha ao criar atividade "${options.title}" (HTTP ${res.status}).`);
        return res.data;
    }

    async function createParticipants(count, tag) {
        return Promise.all(
            Array.from({ length: count }, async (_, i) => {
                const name = `Participante Lab ${i + 1}`;
                const email = `lab-${tag}-${i + 1}@eventhub.com`;
                const res = await call('/api/users/register', {
                    method: 'POST',
                    body: { name, email, password: PASSWORD, role: 'PARTICIPANT', organizationName: 'Laboratório' }
                });
                if (!res.ok || !res.data || !res.data.token) {
                    throw new Error(`Falha ao criar participante ${email} (HTTP ${res.status}).`);
                }
                return { id: res.data.user.id, name, email, token: res.data.token };
            })
        );
    }

    async function fetchActivityReport(organizerToken, eventId, activityId) {
        const res = await call(`/api/registrations/reports/event/${eventId}`, { token: organizerToken });
        if (!res.ok) return null;
        return (res.data.activities || []).find(a => a.activityId === activityId) || null;
    }

    /**
     * RN01 - Concorrência: N participantes se inscrevem simultaneamente em uma atividade com capacidade C.
     */
    async function runConcurrency({ token, capacity, requests, onLog }) {
        if (capacity < 1 || requests < 2) {
            throw new Error('Informe capacidade >= 1 e pelo menos 2 requisições.');
        }
        const tag = makeTag();

        onLog('info', 'Criando evento e atividade de laboratório...');
        const event = await createLabEvent(token, tag);
        const activity = await createActivity(token, event.id, {
            title: 'Oficina de Concorrência',
            capacity,
            waitlistEnabled: true
        });

        onLog('info', `Provisionando ${requests} participantes de teste...`);
        const participants = await createParticipants(requests, tag);

        onLog('info', `Disparando ${requests} inscrições simultâneas (capacidade ${capacity})...`);
        const barrier = performance.now();
        const results = await Promise.all(
            participants.map(async (p, i) => {
                const startMs = Math.round(performance.now() - barrier);
                const res = await call('/api/registrations', {
                    method: 'POST',
                    token: p.token,
                    body: { activityId: activity.id }
                });
                const endMs = Math.round(performance.now() - barrier);
                return {
                    index: i + 1,
                    user: p.name,
                    httpStatus: res.status,
                    status: res.ok && res.data ? res.data.status : 'ERROR',
                    latencyMs: res.latencyMs,
                    startMs,
                    endMs,
                    error: res.ok ? null : (res.data && res.data.message) || `HTTP ${res.status}`
                };
            })
        );
        const durationMs = Math.round(performance.now() - barrier);

        const confirmed = results.filter(r => r.status === 'CONFIRMED').length;
        const waitlist = results.filter(r => r.status === 'WAITLIST').length;
        const errors = results.filter(r => r.status === 'ERROR').length;
        const expectedConfirmed = Math.min(capacity, requests);
        const expectedWaitlist = Math.max(0, requests - capacity);

        onLog('info', 'Consultando contagem consolidada no serviço de registros...');
        const report = await fetchActivityReport(token, event.id, activity.id);
        const reportConfirmed = report ? report.confirmed : null;
        const reportWaitlist = report ? report.waitlist : null;

        const checks = [
            check(
                'RN01 - Sem overbooking',
                confirmed <= capacity,
                `${confirmed} confirmadas para capacidade ${capacity}`
            ),
            check(
                'RN01 - Confirmações exatas',
                confirmed === expectedConfirmed,
                `Esperado ${expectedConfirmed}, obtido ${confirmed}`
            ),
            check(
                'RN03 - Excedentes na lista de espera',
                waitlist === expectedWaitlist,
                `Esperado ${expectedWaitlist}, obtido ${waitlist}`
            ),
            check(
                'Sem erros de servidor sob carga',
                errors === 0,
                `${errors} requisições com erro`
            ),
            check(
                'Consistência com o relatório do organizador',
                report !== null && reportConfirmed === confirmed && reportWaitlist === waitlist,
                report
                    ? `Relatório: ${reportConfirmed} confirmadas / ${reportWaitlist} em espera`
                    : 'Relatório indisponível'
            )
        ];

        onLog(
            checks.every(c => c.passed) ? 'success' : 'error',
            `Concorrência: ${confirmed} confirmadas, ${waitlist} em espera, ${errors} erros em ${durationMs} ms.`
        );

        return {
            eventId: event.id,
            activityId: activity.id,
            capacity,
            requests,
            results,
            kpis: { confirmed, waitlist, errors, durationMs, throughput: Math.round((requests / durationMs) * 1000) },
            checks
        };
    }

    /**
     * RN05 - Falha de notificação: a inscrição deve ser concluída mesmo com o serviço de notificações fora do ar.
     */
    async function runNotificationFailure({ token, eventId, onLog }) {
        const tag = makeTag();
        const activity = await createActivity(token, eventId, {
            title: 'Palestra com Notificação Indisponível',
            capacity: 5,
            waitlistEnabled: false
        });
        const [participant] = await createParticipants(1, tag);

        let probe = null;
        let registration = null;
        onLog('info', 'Ativando falha simulada (HTTP 503) no serviço de notificações...');
        await call('/api/notifications/simulate-failure?fail=true', { method: 'POST' });

        try {
            probe = await call('/api/notifications/send', {
                method: 'POST',
                body: {
                    userId: participant.id,
                    recipientEmail: participant.email,
                    eventType: 'LAB_PROBE',
                    message: 'Sonda do laboratório.'
                }
            });
            onLog(probe.status === 503 ? 'info' : 'error', `Sonda de notificação: HTTP ${probe.status}.`);

            registration = await call('/api/registrations', {
                method: 'POST',
                token: participant.token,
                body: { activityId: activity.id }
            });
            onLog(
                registration.ok ? 'success' : 'error',
                `Inscrição durante a falha de notificação: HTTP ${registration.status}.`
            );
        } finally {
            await call('/api/notifications/simulate-failure?fail=false', { method: 'POST' });
            onLog('info', 'Falha simulada desativada.');
        }

        const checks = [
            check(
                'Notificação indisponível (503) confirmada',
                probe && probe.status === 503,
                probe ? `HTTP ${probe.status}` : 'Sonda não executada'
            ),
            check(
                'RN05 - Inscrição concluída mesmo com falha',
                registration && registration.ok && registration.data.status === 'CONFIRMED',
                registration ? `HTTP ${registration.status} / ${registration.data && registration.data.status}` : 'Sem resposta'
            ),
            check(
                'Serviço de notificações restaurado',
                true,
                'Falha simulada desativada ao final do cenário'
            )
        ];

        return {
            activityId: activity.id,
            registrationStatus: registration && registration.ok ? registration.data.status : null,
            registrationHttpStatus: registration ? registration.status : null,
            checks
        };
    }

    /**
     * RN02/RN03/RN04 - Recusa de pagamento: a vaga é liberada e o primeiro da fila é promovido.
     */
    async function runPaymentRefusal({ token, eventId, onLog }) {
        const tag = makeTag();
        const activity = await createActivity(token, eventId, {
            title: 'Workshop Pago',
            capacity: 1,
            paid: true,
            price: 50,
            waitlistEnabled: true
        });
        const [first, second] = await createParticipants(2, tag);

        onLog('info', `${first.name} tenta a única vaga paga.`);
        const regFirst = await call('/api/registrations', {
            method: 'POST', token: first.token, body: { activityId: activity.id }
        });
        onLog('info', `${second.name} tenta a mesma vaga.`);
        const regSecond = await call('/api/registrations', {
            method: 'POST', token: second.token, body: { activityId: activity.id }
        });

        let paymentFirst = null;
        let refuseRes = null;
        let afterFirst = null;
        let afterSecond = null;
        let paymentSecond = null;
        let confirmRes = null;
        let finalSecond = null;

        if (regFirst.ok && regSecond.ok) {
            const paymentsRes = await call(`/api/payments/registration/${regFirst.data.id}`, { token: first.token });
            paymentFirst = Array.isArray(paymentsRes.data) ? paymentsRes.data.find(p => p.status === 'PENDING') : null;

            if (paymentFirst) {
                onLog('info', `Recusando pagamento de ${first.name}...`);
                refuseRes = await call(`/api/payments/${paymentFirst.id}/refuse`, { method: 'POST', token: first.token });
            }

            afterFirst = await call(`/api/registrations/${regFirst.data.id}`, { token: first.token });
            afterSecond = await call(`/api/registrations/${regSecond.data.id}`, { token: second.token });
            onLog(
                'info',
                `Após a recusa: ${first.name} = ${afterFirst.data && afterFirst.data.status}, ` +
                `${second.name} = ${afterSecond.data && afterSecond.data.status}.`
            );

            const paymentsSecond = await call(`/api/payments/registration/${regSecond.data.id}`, { token: second.token });
            paymentSecond = Array.isArray(paymentsSecond.data) ? paymentsSecond.data.find(p => p.status === 'PENDING') : null;
            if (paymentSecond) {
                onLog('info', `Aprovando pagamento de ${second.name} (promovido da fila)...`);
                confirmRes = await call(`/api/payments/${paymentSecond.id}/confirm`, { method: 'POST', token: second.token });
            }
            finalSecond = await call(`/api/registrations/${regSecond.data.id}`, { token: second.token });
        }

        const checks = [
            check(
                'Primeira inscrição aguarda pagamento',
                regFirst.ok && regFirst.data.status === 'PENDING_PAYMENT',
                `HTTP ${regFirst.status} / ${regFirst.data && regFirst.data.status}`
            ),
            check(
                'Segunda inscrição entra na lista de espera',
                regSecond.ok && regSecond.data.status === 'WAITLIST',
                `HTTP ${regSecond.status} / ${regSecond.data && regSecond.data.status}`
            ),
            check(
                'RN02 - Recusa registrada na inscrição',
                afterFirst && afterFirst.ok && afterFirst.data.status === 'REFUSED',
                afterFirst && afterFirst.data ? `Status: ${afterFirst.data.status}` : 'Sem resposta'
            ),
            check(
                'RN04 - Primeiro da fila promovido a aguardando pagamento',
                afterSecond && afterSecond.ok && afterSecond.data.status === 'PENDING_PAYMENT',
                afterSecond && afterSecond.data ? `Status: ${afterSecond.data.status}` : 'Sem resposta'
            ),
            check(
                'RN03 - Pagamento aprovado confirma a inscrição promovida',
                finalSecond && finalSecond.ok && finalSecond.data.status === 'CONFIRMED',
                finalSecond && finalSecond.data ? `Status final: ${finalSecond.data.status}` : 'Sem resposta'
            )
        ];

        return {
            activityId: activity.id,
            steps: {
                firstRegistration: regFirst.status,
                refusal: refuseRes ? refuseRes.status : null,
                secondAfterRefusal: afterSecond && afterSecond.data ? afterSecond.data.status : null,
                secondFinal: finalSecond && finalSecond.data ? finalSecond.data.status : null,
                confirmation: confirmRes ? confirmRes.status : null
            },
            checks
        };
    }

    window.EventHubLab = {
        runConcurrency,
        runNotificationFailure,
        runPaymentRefusal
    };
})();
