// Material UI
import Grid from "@material-ui/core/Grid";
import Typography from "@material-ui/core/Typography";
import Button from "@material-ui/core/Button";

// React translations
import { useTranslation } from "react-i18next";

// Styling
import styles from "./outlinedTable.module.css";

// Components
import Spinner from "../Spinner/index";

// Miner past deadlines graph
import MinerDeadLinesGraph from "../../miner/minerDeadlines/index";

const OutLinedTable = (props) => {
  // Translations details
  const { t } = useTranslation();

  const { isLoading, data, notFoundLabel, onClickLastItem } = props;

  let content = null;

  // Check if user has not found any results
  if ((!data || (!data.length && data.length === 0)) && isLoading === false) {
    content = (
      <Typography
        variant="h6"
        color="textSecondary"
        align="center"
        style={{ width: "100%", fontSize: 18 }}
      >
        {notFoundLabel}
      </Typography>
    );
  } else if (data && data.length && data.length > 0) {
    content = data.map((item, key) => {
      // Check if item has value
      if (!item || !item.type) {
        return null;
      }

      // Render type of data
      switch (item.type) {
        // Only info row
        case "info":
          // Check if user has dynamic Onlick
          const onClickAction =
            item.onClick && item.onClick !== null && item.onClick !== undefined
              ? item.onClick
              : null;

          // Check if user has dynamic onClick, but in the value column
          const onClickValueAction =
            item.onClickValue &&
            item.onClickValue !== null &&
            item.onClickValue !== undefined
              ? item.onClickValue
              : null;

          return (
            <Grid container item className={styles.tableItem} key={key}>
              <Grid
                item
                className={styles.tableItemLeftSide}
                style={{
                  width: props.fWidth,
                  cursor: onClickAction ? "pointer" : null,
                }}
                onClick={onClickAction}
              >
                <Typography>{item.title}</Typography>
                {item.sTitle && item.sTitle.trim() !== "" ? (
                  <Typography component="span">{item.sTitle}</Typography>
                ) : null}
              </Grid>

              <Grid
                item
                className={styles.tableItemRightSide}
                style={{
                  width: props.sWidth,
                  cursor: onClickValueAction ? "pointer" : null,
                }}
                onClick={onClickValueAction}
              >
                <Typography>{item.value}</Typography>
              </Grid>
            </Grid>
          );

        // Last row action
        case "action":
          return (
            <Grid item className={styles.tableItem} key={key}>
              <Button className={styles.optionBtn} onClick={onClickLastItem}>
                {t(item.label)}
              </Button>
            </Grid>
          );

        // Custom row
        case "BookmarkedMinerDeadlines":
          return (
            <Grid
              container
              item
              className={styles.tableItem}
              key={key}
              wrap="wrap"
              direction="column"
            >
              <Grid
                item
                className={styles.tableItemLeftSide}
                style={{ width: "100%" }}
              >
                <Typography>{item.title}</Typography>
              </Grid>

              {/* Miners past deadlines graph */}
              <Grid item style={{ width: "100%" }}>
                <MinerDeadLinesGraph chartHeight={250} />
              </Grid>
            </Grid>
          );

        default:
          return null;
      }
    });
  }

  return (
    <Grid
      container
      direction="column"
      justifyContent="flex-start"
      alignItems="flex-start"
      className={styles.outlinedTable}
    >
      {isLoading === true ? <Spinner /> : content}
    </Grid>
  );
};

// Type of data
// info:   title, sTitle, value, fWidth, sWidth
// action: label, onClick

export default OutLinedTable;
