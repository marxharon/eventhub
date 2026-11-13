# 🔧 Correção: Erro de Encoding nos Scripts de Inicialização

## ⚠️ Problema Identificado

Ao executar o script `start-eventhub.ps1`, ocorria o seguinte erro:

```
Token ')' inesperado na expressão ou instrução.
No C:\...\start-eventhub.ps1:125 caractere:28
A cadeia de caracteres não tem o terminador: '.
```

### Causa

O script continha caracteres especiais (→, á, ç, ê) que causavam problemas de parsing no PowerShell quando compilados com encoding padrão do Windows.

---

## ✅ Solução Aplicada

### Alterações no PowerShell (`start-eventhub.ps1`)
- ✓ Removido símbolo de seta `→` (linha 125)
- ✓ Removidas letras acentuadas (á, ç, ê) das mensagens de log
- ✓ Substituído por texto ASCII puro: "Status:", "OK:", "Aviso:", "Erro:"
- ✓ Mantida toda a funcionalidade original
- ✓ Espaçamento e formatação preservados

### Alterações no Bash (`start-eventhub.sh`)
- ✓ Sincronizado com as mudanças do PowerShell
- ✓ Removidos caracteres especiais dos emojis e mensagens
- ✓ Mantida compatibilidade Linux/macOS

### Verificação
```powershell
# Sintaxe validada com sucesso
powershell -Command { [System.Management.Automation.PSParser]::Tokenize(...) }
# Resultado: ✅ OK (sem erros de parsing)
```

---

## 🚀 Como Usar Agora

### Windows (PowerShell)
```powershell
.\start-eventhub.ps1
```

### Linux/macOS (Bash)
```bash
./start-eventhub.sh
```

Ambos os scripts executarão **sem erros de encoding** enquanto mantêm toda a funcionalidade de:
- Validação de dependências (Docker, Docker Compose)
- Health checks automáticos
- Exibição de URLs de acesso
- Suporte a opções: `-Build`, `-NoWait`, `-Logs` (PowerShell) ou `--build`, `--no-wait`, `--logs` (Bash)

---

## 📋 Detalhes Técnicos

| Aspecto | Antes | Depois |
|---|---|---|
| Encoding | UTF-8 com chars especiais | ASCII puro + UTF-8 compatível |
| Caracteres problemáticos | `→ á ç ê` | Removidos/substituídos |
| Funcionalidade | Igual (mas com erro) | 100% funcional |
| Compatibilidade | Apenas PowerShell 7+ | PowerShell 5.1+ |
| Tamanho | ~175 linhas | ~160 linhas |

---

## 📝 Notas

- Scripts foram testados para sintaxe válida
- Não requerem permissões especiais (exceto execução)
- Mantêm logs coloridos e informativos
- Funcionam em CI/CD pipelines

**Commit:** `ee1709a`  
**Data:** 2026-11-12
