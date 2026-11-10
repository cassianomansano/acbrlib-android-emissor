# ACBrLib Android · Emissor de testes (NF-e e NFC-e)

App Android de exemplo que **emite NF-e (modelo 55) e NFC-e (modelo 65) em homologação** usando a
**ACBrLib Android** do [Projeto ACBr](https://www.projetoacbr.com.br), com **impressão do cupom em impressora
térmica Bluetooth** (Daruma ou ESC/POS).

Feito em **Java puro** (sem AndroidX), para quem quer ver, num código curto, o caminho completo:
certificado A1 → montar a nota → assinar → validar → enviar → PDF/XML → imprimir.

> ⚠️ **Somente homologação.** O app sempre configura a biblioteca com `NFe/Ambiente=1` (homologação) e não tem
> opção de produção. Documentos sem valor fiscal.

## O que o app faz

| Aba | Funções |
|---|---|
| **Configurar** | Importa o certificado `.pfx` do aparelho; senha e CSC guardados cifrados com o **Android Keystore** (veja "Limitações"); dados do emitente, destinatário e responsável técnico; "Testar SEFAZ"; impressora Bluetooth pareada, modo Daruma/ESC-POS e impressão de teste |
| **Emitir** | Modelo 55/65, série, última nota e forma de pagamento. Tenta o próximo número; em **duplicidade** (cStat 204/539) soma 1 e tenta de novo a cada 1 s; **Cancelar operação** interrompe. Mostra as últimas mensagens |
| **Emitidas** | Histórico com chave e protocolo; **Abrir PDF**, **Compartilhar** (PDF + XML, ex.: WhatsApp) e **Imprimir** (NFC-e) |

A nota de teste tem 5 produtos de exemplo (Simples Nacional, CSOSN 102), desconto de 10% e pagamento misto
(dinheiro + cartão ou PIX). Em homologação a SEFAZ exige o nome
`NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL` no destinatário (o app preenche).

## Como rodar

1. **ACBrLib Android** — copie para `app/libs/`:
   - `ACBrLibComum-release.aar` e `ACBrLibNFe-release.aar`.
   - Use bibliotecas **compatíveis com Android 11+** (veja "Pegadinhas"). As do pacote Android do
     [Build ACBr](https://apoio.build-acbr.workers.dev) (serviço pago da Calunaty Sistemas, autora deste exemplo) já vêm prontas; também é possível compilar a partir dos fontes do ACBr.
2. **Schemas** — copie a pasta `Schemas/NFe` do ACBr para `app/src/main/assets/Schemas/NFe/`.
3. Abra a pasta no **Android Studio** (AGP 8.7.3, JDK 17) e rode no aparelho.
4. No app: **Configurar** → importe o `.pfx`, digite a senha (e o ID do CSC + CSC de homologação para NFC-e),
   preencha os dados da nota → **Testar SEFAZ** (esperado `107`) → **Emitir**.

Requisitos: Android 8.0+ (API 26) recomendado; no Android 7 a aba **Emitidas** não abre. Para imprimir, pareie a impressora nas configurações de Bluetooth do Android antes.

## Como funciona (arquivos)

| Arquivo | Papel |
|---|---|
| `NotaTeste.java` | Monta o INI da nota no formato da ACBrLibNFe (classe pura, sem Android) |
| `Emissor.java` | Laço `CarregarINI → Assinar → Validar → Enviar`, tratamento de duplicidade, rede e rejeição |
| `Cofre.java` | Guarda senha do certificado e CSC cifrados com AES-GCM; a chave fica no Android Keystore |
| `Impressora.java` | Envia o cupom por Bluetooth (perfil SPP) em blocos |
| `Arquivos.java` | `ContentProvider` mínimo para abrir/compartilhar PDF e XML |
| `MainActivity.java` | Telas e configuração da biblioteca |

## Pegadinhas que este exemplo já resolve

- **Bluetooth:** a ACBrLib compilada com Free Pascal não tem Bluetooth nativo no Android. O app manda a
  biblioteca gerar o cupom num arquivo (`PosPrinter.Porta = FILE:.../cupom.prn`) e envia os bytes pelo Bluetooth.
  Use extensão **`.prn`**: com `.txt` o ACBr grava só texto, sem QR Code e sem corte.
- **Android 11+:** bibliotecas do Free Pascal 3.2.2 sem correção podem fechar o app ao carregar ou ao tratar
  exceções internas (leitura de memória fora da área válida). Use uma build corrigida.
- **Configuração:** `DFe/SSLCryptLib=1`, `SSLHttpLib=3`, `SSLXmlSignLib=4` (OpenSSL + LibXML2); caminhos absolutos
  dentro do app; `Principal/LogNivel=0` (no Android o log nasce no nível máximo).
- **`verProc`** aceita no máximo 20 caracteres; **série** vai de 0 a 999.
- **Android 12+:** só precisa da permissão `BLUETOOTH_CONNECT` (o app não faz busca de aparelhos).
- A impressora pode estar em modo Daruma ou ESC/POS: se o cupom sair estranho, troque o modo.

## Limitações (é um exemplo, não um emissor de produção)

- Ao configurar a biblioteca, a senha e o CSC também ficam no `acbrlib.ini`, na área privada do app (senha com a
  criptografia padrão da ACBrLib, CSC em texto). Em produção, use uma `ChaveCrypt` própria e não deixe o INI no aparelho.
- O laço de duplicidade e de falha de rede **não tem limite de tentativas**: use **Cancelar operação**.
- Não gire a tela durante a emissão (a tela é recriada e a emissão em andamento não é cancelada).
- Depois de um tempo esgotado, a nova tentativa gera outra nota: em produção, consulte a chave antes de reenviar.
- A nota de teste usa PIS/COFINS CST 01 com Simples Nacional; ajuste a tributação (ex.: CST 49) à sua realidade.

## Guia

Guia "ACBrLib no Android" (o que muda em relação à DLL, passo a passo, exemplos oficiais):
<https://apoio.build-acbr.workers.dev/android>

---

**[Calunaty Sistemas](https://www.calunaty.com.br)** · *Transformando ideias em soluções*

Licença MIT (veja `LICENSE`). Avisos sobre o Projeto ACBr, marcas e uso: veja [`AVISOS.md`](AVISOS.md).
