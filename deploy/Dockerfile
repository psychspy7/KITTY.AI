FROM python:3.12-slim
WORKDIR /app
COPY requirements-cloud.txt ./
RUN pip install --no-cache-dir -r requirements-cloud.txt
COPY server ./server
RUN useradd --uid 10001 --create-home kitty && mkdir /data && chown kitty:kitty /data
USER kitty
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 KITTY_DATA_DIR=/data
EXPOSE 8765
CMD ["python", "-m", "server.hosted"]
