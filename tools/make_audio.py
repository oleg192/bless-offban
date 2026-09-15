from pathlib import Path
import subprocess

target = Path('web/audio')
target.mkdir(parents=True, exist_ok=True)
phrases = {
    'ready': 'Бортовые системы готовы. Приступайте к заданию.',
    'altitude': 'Высота. Наберите высоту.',
    'lock': 'Внимание. Облучение.',
    'missile': 'Ракета. Выполните манёвр.',
    'target': 'Цель захвачена.',
    'destroyed': 'Цель уничтожена.',
    'incoming': 'Обнаружены воздушные цели.',
    'damage': 'Повреждение самолёта.',
    'stall': 'Скорость. Скорость.',
    'win': 'Задание выполнено. Возвращайтесь на базу.',
    'supply': 'Боезапас пополнен. Самолёт восстановлен.',
    'boundary': 'Вернитесь в зону операции.',
    'empty': 'Боезапас израсходован.',
    'flares': 'Ловушки отстреляны.'
}
for name, text in phrases.items():
    subprocess.run(['espeak-ng', '-v', 'ru+f3', '-s', '145', '-p', '46', '-a', '135',
                    '-w', str(target / (name + '.wav')), text], check=True)
print(f'Created {len(phrases)} embedded Russian voice clips')
