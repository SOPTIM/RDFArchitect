FROM nginx:alpine3.22

#RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh

#RUN mkdir -p /var/cache/nginx /var/tmp/nginx \
#    && chown -R appuser:appgroup /var/cache/nginx /var/tmp/nginx /usr/share/nginx/html

#USER appuser

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
