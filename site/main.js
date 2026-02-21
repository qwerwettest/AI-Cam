const fileInput = document.getElementById('fileInput');
const sheetSelect = document.getElementById('sheetSelect');
const preview = document.getElementById('preview');
const sendBtn = document.getElementById('sendBtn');
const statusEl = document.getElementById('status');
const endpointInput = document.getElementById('endpoint');
const fileInfoEl = document.getElementById('fileInfo');
const tableTemplate = document.getElementById('tableTemplate');
const payloadPreview = document.getElementById('payloadPreview');
const payloadMeta = document.getElementById('payloadMeta');
const copyPayloadBtn = document.getElementById('copyPayloadBtn');

let workbook = null;
let currentSheet = null;
let currentFileName = null;
let lastPayloadText = '';

fileInput.addEventListener('change', handleFile);
sheetSelect.addEventListener('change', () => renderSheet(sheetSelect.value));
sendBtn.addEventListener('click', sendToJava);
copyPayloadBtn.addEventListener('click', copyPayload);

function handleFile(event) {
  const file = event.target.files[0];
  workbook = null;
  currentSheet = null;
  currentFileName = file ? file.name : null;
  sheetSelect.innerHTML = '';
  sheetSelect.disabled = true;
  sendBtn.disabled = true;
  setStatus('', '');
  preview.textContent = 'Загружаем файл…';
  updatePayloadPreview();

  if (!file) {
    preview.textContent = 'Файл не выбран.';
    return;
  }

  fileInfoEl.textContent = `${file.name} — ${(file.size / 1024).toFixed(1)} KB`;

  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target.result);
      workbook = XLSX.read(data, { type: 'array' });
      if (!workbook.SheetNames.length) {
        preview.textContent = 'Листов не найдено.';
        return;
      }
      fillSheetSelect(workbook.SheetNames);
      renderSheet(workbook.SheetNames[0]);
    } catch (err) {
      console.error(err);
      preview.textContent = 'Не удалось прочитать файл. Убедитесь, что это Excel (.xlsx/.xls).';
    }
  };
  reader.readAsArrayBuffer(file);
}

function fillSheetSelect(sheetNames) {
  sheetSelect.disabled = false;
  sheetSelect.innerHTML = '';
  sheetNames.forEach((name) => {
    const option = document.createElement('option');
    option.value = name;
    option.textContent = name;
    sheetSelect.appendChild(option);
  });
}

function renderSheet(sheetName) {
  if (!workbook || !sheetName) return;

  // копируем лист и разворачиваем merged-ячейки, чтобы значения не терялись
  const sheet = expandMerges(structuredClone(workbook.Sheets[sheetName]));
  const allRows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' });

  // первая непустая строка считаем заголовком
  const headerIdx = allRows.findIndex((row) => row.some((c) => String(c).trim() !== ''));
  const headerRow = headerIdx >= 0 ? allRows[headerIdx] : [];
  const bodyRows = headerIdx >= 0 ? allRows.slice(headerIdx + 1) : [];

  // убираем полностью пустые строки
  const denseBody = bodyRows.filter((row) => row.some((c) => String(c).trim() !== ''));

  const rows = [headerRow, ...denseBody];
  currentSheet = { name: sheetName, rows };
  sendBtn.disabled = rows.length === 0;

  if (!rows.length) {
    preview.textContent = 'Лист пустой.';
    updatePayloadPreview();
    return;
  }

  const [cleanHeader = [], ...body] = rows;
  const fragment = tableTemplate.content.cloneNode(true);
  const thead = fragment.querySelector('thead');
  const tbody = fragment.querySelector('tbody');

  const headerTr = document.createElement('tr');
  cleanHeader.forEach((cell, idx) => {
    const th = document.createElement('th');
    th.textContent = String(cell || `col_${idx + 1}`);
    headerTr.appendChild(th);
  });
  thead.appendChild(headerTr);

  const maxRows = 50; // preview limit
  body.slice(0, maxRows).forEach((row) => {
    const tr = document.createElement('tr');
    cleanHeader.forEach((_, idx) => {
      const td = document.createElement('td');
      td.textContent = row[idx] ?? '';
      tr.appendChild(td);
    });
    tbody.appendChild(tr);
  });

  preview.innerHTML = '';
  const scroll = document.createElement('div');
  scroll.className = 'table-scroll';
  scroll.appendChild(fragment);
  preview.appendChild(scroll);

  if (body.length > maxRows) {
    const note = document.createElement('div');
    note.className = 'muted';
    note.textContent = `Показаны первые ${maxRows} строк из ${body.length}.`;
    preview.appendChild(note);
  }

  updatePayloadPreview();
}

async function sendToJava() {
  const payload = buildPayload();
  if (!payload) {
    setStatus('Нет данных для отправки.', 'status-warn');
    return;
  }

  const endpoint = endpointInput.value.trim();
  if (!endpoint) {
    setStatus('Укажите endpoint Java сервиса.', 'status-warn');
    return;
  }

  setStatus('Отправляем…', 'status-warn');
  sendBtn.disabled = true;

  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(`${response.status} ${response.statusText}: ${text}`);
    }

    setStatus('Успешно отправлено в Java.', 'status-ok');
  } catch (err) {
    console.error(err);
    setStatus(`Ошибка отправки: ${err.message}`, 'status-error');
  } finally {
    sendBtn.disabled = false;
  }
}

function buildPayload() {
  if (!currentSheet) return null;
  const fileName = currentFileName || fileInput.files[0]?.name || 'upload.xlsx';
  const schedule = transformRowsToSchedule(currentSheet.rows, currentSheet.name);
  return {
    fileName,
    sheet: currentSheet.name,
    rows: currentSheet.rows,
    schedule,
  };
}

function updatePayloadPreview() {
  const payload = buildPayload();
  if (!payload) {
    payloadPreview.textContent = 'Нет данных для отображения.';
    payloadMeta.textContent = '';
    copyPayloadBtn.disabled = true;
    lastPayloadText = '';
    return;
  }

  const json = JSON.stringify(payload, null, 2);
  const limit = 4000;
  let display = json;
  let note = `Размер JSON: ${json.length} символов.`;
  if (json.length > limit) {
    display = `${json.slice(0, limit)}\n... (усечено, показаны первые ${limit} символов)`;
    note += ` Усечено до ${limit} символов для предпросмотра.`;
  }

  payloadPreview.textContent = display;
  payloadMeta.textContent = note;
  copyPayloadBtn.disabled = false;
  lastPayloadText = json;
}

async function copyPayload() {
  if (!lastPayloadText) return;
  try {
    await navigator.clipboard.writeText(lastPayloadText);
    payloadMeta.textContent = `JSON скопирован в буфер (${lastPayloadText.length} символов).`;
  } catch (err) {
    console.error(err);
    payloadMeta.textContent = 'Не удалось скопировать JSON (разрешите доступ к буферу обмена).';
  }
}

function setStatus(message, cls) {
  statusEl.textContent = message;
  statusEl.className = `muted ${cls || ''}`.trim();
}

// заполняем merged-ячейки значениями из верхней-левой ячейки диапазона
function expandMerges(sheet) {
  const merges = sheet['!merges'] || [];
  merges.forEach((range) => {
    const start = XLSX.utils.decode_cell(range.s);
    const end = XLSX.utils.decode_cell(range.e);
    const baseRef = XLSX.utils.encode_cell(range.s);
    const baseCell = sheet[baseRef];
    if (!baseCell || baseCell.v === undefined || baseCell.v === null) return;

    for (let r = start.r; r <= end.r; r += 1) {
      for (let c = start.c; c <= end.c; c += 1) {
        const ref = XLSX.utils.encode_cell({ r, c });
        if (!sheet[ref] || sheet[ref].v === undefined || sheet[ref].v === null) {
          sheet[ref] = { t: baseCell.t, v: baseCell.v };
        }
      }
    }
  });
  return sheet;
}

// Преобразуем табличный формат (3 строки на урок, дни по колонкам) в структуру, похожую на schedule.json
function transformRowsToSchedule(rows, sheetName) {
  if (!rows || rows.length === 0) return { groups_schedule: {} };

  // Можно подстроить: на сколько дней есть колонки. Берём с 3-й колонки и дальше.
  const baseCol = 2; // 0: номер, 1: время, начиная с 2 — дни
  const dayNamesDefault = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  const maxDayCols = Math.max(0, (rows[0]?.length || 0) - baseCol);
  const dayNames = dayNamesDefault.slice(0, maxDayCols);

  const days = dayNames.map((day) => ({ day, lessons: [] }));

  for (let r = 0; r < rows.length; r += 3) {
    const rowSubj = rows[r] || [];
    const rowTeach = rows[r + 1] || [];
    const rowRoom = rows[r + 2] || [];

    const num = rowSubj[0];
    const time = String(rowSubj[1] || '').trim();

    dayNames.forEach((_, idx) => {
      const c = baseCol + idx;
      const subject = String(rowSubj[c] || '').trim();
      const teacher = String(rowTeach[c] || '').trim();
      const room = String(rowRoom[c] || '').trim();

      if (subject || teacher || room) {
        days[idx].lessons.push({
          num,
          time,
          subject,
          teacher,
          room,
        });
      }
    });
  }

  // убираем пустые дни
  const nonEmptyDays = days.filter((d) => d.lessons.length > 0);

  const schedule = {
    college: 'АУЭС',
    academic_year: '2025-2026',
    course: 3,
    semester: 2,
    department: 'Русское отделение (на базе 9 классов)',
    groups_schedule: {
      [sheetName]: nonEmptyDays,
    },
  };

  return schedule;
}

// Функция отправки расписания на Spring Boot backend
async function sendScheduleToServer() {
  // Собираем данные из таблицы preview
  const previewTable = document.querySelector('#preview table tbody');
  if (!previewTable) {
    alert('Таблица расписания не найдена. Загрузите файл сначала.');
    return;
  }

  const rows = Array.from(previewTable.querySelectorAll('tr'));
  console.log('Найдено строк в таблице:', rows.length);
  
  const scheduleData = [];

  rows.forEach((tr, idx) => {
    const cells = tr.querySelectorAll('td');
    console.log(`Строка ${idx}: колонок = ${cells.length}`);
    
    if (cells.length < 5) return; // недостаточно колонок

    const day = String(cells[0].textContent || '').trim();
    const time = String(cells[1].textContent || '').trim();
    const subject = String(cells[2].textContent || '').trim();
    const teacher = String(cells[3].textContent || '').trim();
    const room = String(cells[4].textContent || '').trim();

    // Пропускаем строки, где нет обязательных полей
    if (!day || !time || !subject || !teacher) {
      console.log(`Строка ${idx} пропущена (неполные данные):`, { day, time, subject, teacher });
      return;
    }

    scheduleData.push({
      "День": day,
      "Время": time,
      "Предмет": subject,
      "Преподаватель": teacher,
      "Кабинет": room
    });
  });

  console.log('Сформировано записей:', scheduleData.length);
  console.log('Данные для отправки:', scheduleData);

  if (scheduleData.length === 0) {
    alert('Нет корректных строк для отправки. Проверьте данные в таблице (откройте консоль F12 для деталей).');
    return;
  }

  // Формируем JSON и создаём Blob
  const jsonText = JSON.stringify(scheduleData, null, 2);
  console.log('JSON для отправки:\n', jsonText);

  const form = new FormData();
  form.append("file", new Blob([jsonText], { type: "application/json" }), "schedule.json");

  try {
    const res = await fetch("http://localhost:3333/api/schedule/upload", {
      method: "POST",
      body: form
    });

    console.log('← Ответ сервера. Статус:', res.status);

    if (res.ok) {
      const data = await res.json();
      console.log('← Данные ответа:', data);
      const inserted = data.inserted || data.count || scheduleData.length;
      alert(`✅ Успешно! Добавлено записей: ${inserted}`);
    } else {
      const errorText = await res.text();
      console.error('← Ошибка сервера:', errorText);
      
      let errorData;
      try {
        errorData = JSON.parse(errorText);
      } catch {
        errorData = { message: errorText };
      }
      
      const message = errorData.message || errorData.error || errorText || `HTTP ${res.status}`;
      alert(`❌ Ошибка ${res.status}:\n${message}\n\nПроверьте консоль Spring Boot для деталей`);
    }
  } catch (err) {
    console.error('Ошибка отправки:', err);
    
    let errorMsg = err.message;
    if (err.message.includes('NetworkError') || err.message.includes('Failed to fetch')) {
      errorMsg = `Не удалось подключиться к серверу.\n\n` +
                 `Возможные причины:\n` +
                 `1. Spring Boot сервер не запущен на localhost:2222\n` +
                 `2. CORS: сервер должен разрешить запросы с вашего домена\n` +
                 `3. Брандмауэр блокирует соединение`;
    }
    alert(`❌ Ошибка отправки:\n${errorMsg}`);
  }
}

// Делаем функцию доступной глобально для вызова из HTML
window.sendScheduleToServer = sendScheduleToServer;
