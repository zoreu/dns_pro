# DNS PRO

DNS PRO resolver para Android TV, Google TV e TV Box.

## Uso

1. Abra o app DNS PRO.
2. Escolha um perfil DNS: Cloudflare, Google, AdGuard, CleanBrowsing, Quad9 ou Personalizado.
3. Selecione **ATIVAR DNS PRO** e aceite a permissão de VPN local do Android.
4. Minimize o app e use seus apps normalmente.

## Observações técnicas importantes

- O app usa `VpnService` local para aplicar servidores DNS por IP sem rotear o tráfego inteiro da internet para fora.
- O modo atual aplica DNS por IP. Ele **não implementa proxy DoH interno**; campo de host seguro é apenas referência do perfil.
- Se trocar o DNS, desative e ative novamente para garantir que o Android aplique o novo perfil.
- Se a TV exibir erro de “DNS privado não pode ser acessado”, desative o DNS Privado nativo do Android nas configurações de rede.
- A restauração automática após reiniciar depende de permissão VPN ainda válida e das regras do Android/firmware da TV Box.

![DNS PRO](https://i.imgur.com/DWOMSmI.jpeg)
