COMMAND=$1
COMPOSE_PROJECT=helper_$(basename `pwd` | sed 's/-/_/g')_prod
docker-compose -p $COMPOSE_PROJECT -f compose/docker-compose.yml $COMMAND